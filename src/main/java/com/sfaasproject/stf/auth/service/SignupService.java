package com.sfaasproject.stf.auth.service;

import com.sfaasproject.stf.auth.config.TelegramProperties;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.telegram.service.RedisTelegramLinkTokenStore;
import com.sfaasproject.stf.telegram.service.TelegramLinkTokenService;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-005 회원가입 신청. 승인 대기 계정을 만들고 본인용 텔레그램 연결 주소를 발급한다.
 * 직책은 신청자가 고르지 않으며 시스템관리자가 승인할 때 정한다.
 */
@Service
public class SignupService {

    /** ERD account.name 은 varchar(50) 이다. */
    static final int NAME_MAX_LENGTH = 100;

    public record TelegramLink(String linkUrl, String qrPayload, Instant expiresAt) {
        @Override
        public String toString() {
            return "TelegramLink[redacted]";
        }
    }

    public record Signup(long requestId, String loginId, String name, AccountStatus status,
            Instant requestedAt, TelegramLink telegramLink) { }

    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final AuthPolicyService policy;
    private final TelegramLinkTokenService linkTokens;
    private final RedisTelegramLinkTokenStore linkTokenStore;
    private final TelegramProperties telegram;

    public SignupService(AccountRepository accounts, PasswordEncoder passwords,
            AuthPolicyService policy, TelegramLinkTokenService linkTokens,
            RedisTelegramLinkTokenStore linkTokenStore, TelegramProperties telegram) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.policy = policy;
        this.linkTokens = linkTokens;
        this.linkTokenStore = linkTokenStore;
        this.telegram = telegram;
    }

    @Transactional
    public Signup signup(String loginId, String password, String name, Instant now) {
        String cleanName = validate(loginId, password, name);
        if (!telegram.configured()) {
            // 연결 주소를 만들 수 없으면 계정도 만들지 않는다. 설정 누락은 서버 오류로 응답된다.
            throw new IllegalStateException("app.telegram.bot-username is not configured");
        }
        if (accounts.existsByLoginId(loginId)) {
            throw duplicate();
        }
        AccountEntity saved;
        try {
            saved = accounts.saveAndFlush(AccountEntity.signup(loginId, passwords.encode(password),
                    cleanName, KstTime.toLocal(now)));
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(); // 동시에 같은 아이디로 신청한 경우
        }
        TelegramLinkTokenService.Issued issued = linkTokens.issue(now);
        // Redis 저장에 실패하면 예외로 트랜잭션이 취소되어 계정도 만들어지지 않는다.
        linkTokenStore.save(saved.getId(), issued, now);
        String url = "https://t.me/" + telegram.normalizedBotUsername() + "?start=" + issued.rawToken();
        return new Signup(saved.getId(), saved.getLoginId(), saved.getName(),
                AccountStatus.PENDING_APPROVAL, KstTime.toInstant(saved.getCreatedAt()),
                new TelegramLink(url, url, issued.expiresAt()));
    }

    private String validate(String loginId, String password, String name) {
        try {
            policy.validateLoginId(loginId);
            policy.validatePassword(password);
        } catch (IllegalArgumentException ex) {
            throw validationError();
        }
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.length() > NAME_MAX_LENGTH) {
            throw validationError();
        }
        return cleanName;
    }

    private DomainException validationError() {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
    }

    private DomainException duplicate() {
        return new DomainException(HttpStatus.CONFLICT, "USER_LOGIN_ID_DUPLICATE",
                "이미 사용 중인 아이디입니다.");
    }
}

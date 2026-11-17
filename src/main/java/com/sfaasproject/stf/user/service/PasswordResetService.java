package com.sfaasproject.stf.user.service;

import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.auth.service.TemporaryPasswordStore;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USR-005 관리자 비밀번호 초기화. 12자리 임시 비밀번호를 만들어 본인의 텔레그램으로 보내고,
 * 보낸 경우에만 비밀번호를 바꾼다. 임시 비밀번호는 30분 뒤 만료되고 다음 로그인 뒤 변경해야 한다.
 * 기존 토큰은 모두 폐기한다. 임시 비밀번호는 응답, 로그, 변경 이력 어디에도 남기지 않는다.
 */
@Service
public class PasswordResetService {

    static final int LENGTH = 12;
    static final Duration VALIDITY = Duration.ofMinutes(30);

    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "!@#$%*";
    private static final String ALL = LETTERS + DIGITS + SPECIALS;

    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final TemporaryPasswordSender sender;
    private final TemporaryPasswordStore temporaryPasswords;
    private final AuthFlowService.SessionStore sessions;
    private final AuditRecorder audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(AccountRepository accounts, PasswordEncoder passwords,
            TemporaryPasswordSender sender, TemporaryPasswordStore temporaryPasswords,
            AuthFlowService.SessionStore sessions, AuditRecorder audit, Clock clock) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.sender = sender;
        this.temporaryPasswords = temporaryPasswords;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void reset(long userId, long actorId) {
        AccountEntity account = accounts.findForUpdate(userId)
                .filter(found -> !found.isDeleted() && !found.isPending())
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        if (!account.isTelegramConnected()) {
            // 미연결 계정의 전달 방법은 계약이 정해질 때까지 초기화를 거절한다(openapi USR-005).
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "TELEGRAM_NOT_CONNECTED",
                    "텔레그램 연결이 필요합니다.");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(VALIDITY);
        String temporary = generate();
        if (!sender.send(account.getTelegramChatId(), message(account.getName(), temporary))) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "TELEGRAM_DELIVERY_FAILED",
                    "메시지 발송에 실패했습니다.");
        }
        account.resetPassword(passwords.encode(temporary), KstTime.toLocal(now));
        temporaryPasswords.issued(account.getId(), expiresAt);
        sessions.revokeAllForUser(account.getId(), now);
        audit.record(actorId, AuditTargetType.ACCOUNT, account.getLoginId(), "비밀번호 초기화", null,
                "임시 비밀번호 발급 (30분 유효)");
    }

    /** 영문, 숫자, 특수문자를 각각 1개 이상 넣은 12자리. 헷갈리는 글자(0, O, 1, l, I)는 뺐다. */
    String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(LETTERS));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIALS));
        while (chars.size() < LENGTH) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, random);
        StringBuilder result = new StringBuilder(LENGTH);
        chars.forEach(result::append);
        return result.toString();
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }

    private static String message(String name, String temporary) {
        return "[STF] " + name + "님, 비밀번호가 초기화되었습니다.\n"
                + "임시 비밀번호: " + temporary + "\n"
                + "30분 안에 로그인한 뒤 새 비밀번호로 바꿔 주세요.";
    }
}

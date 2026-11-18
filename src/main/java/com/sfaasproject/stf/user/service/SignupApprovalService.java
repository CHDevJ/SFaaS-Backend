package com.sfaasproject.stf.user.service;

import com.sfaasproject.stf.auth.service.AuthPolicyService;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.entity.ApprovalStatus;
import com.sfaasproject.stf.user.event.SignupDecidedEvent;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * APR-001~003 가입 신청 목록, 승인, 거절. 승인과 거절은 줄을 잠그고 처리해서 같은 신청을 동시에
 * 처리하지 못하게 하고, 변경 이력 기록과 한 트랜잭션으로 묶는다. 알림은 이벤트로만 알린다.
 */
@Service
public class SignupApprovalService {

    public record PendingSignup(long requestId, String loginId, String name, AccountStatus accountStatus,
            Instant requestedAt, boolean telegramConnected) { }

    private static final Sort OLDEST_FIRST = Sort.by("createdAt").ascending().and(Sort.by("id").ascending());

    private final AccountRepository accounts;
    private final AuthPolicyService policy;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SignupApprovalService(AccountRepository accounts, AuthPolicyService policy, AuditRecorder audit,
            ApplicationEventPublisher events, Clock clock) {
        this.accounts = accounts;
        this.policy = policy;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** 승인 대기 신청을 오래된 순으로 보여 준다. */
    @Transactional(readOnly = true)
    public Page<PendingSignup> listPending(PageQuery query) {
        return accounts.findByApprovalStatusAndDeletedAtIsNull(ApprovalStatus.PENDING,
                query.toPageable(OLDEST_FIRST)).map(SignupApprovalService::toPending);
    }

    @Transactional
    public UserView approve(long requestId, Permission position, long approverId) {
        Objects.requireNonNull(position);
        AccountEntity account = lockPending(requestId);
        LocalDateTime now = KstTime.toLocal(clock.instant());
        account.approve(position.name(), approverId, now);
        audit.record(approverId, AuditTargetType.ACCOUNT, account.getLoginId(), "가입 승인", "승인 대기",
                "승인 (직책: " + PositionLabels.of(position) + ")");
        events.publishEvent(new SignupDecidedEvent(SignupDecidedEvent.Outcome.APPROVED, account.getId(),
                account.getLoginId(), account.getName(), position.name(), account.getTelegramChatId(), null));
        return UserView.from(account, policy);
    }

    /** 거절한 신청은 줄을 지운다. 같은 아이디로 다시 신청할 수 있어야 하고 참조하는 기록이 없기 때문이다. */
    @Transactional
    public void reject(long requestId, String reason, long approverId) {
        AccountEntity account = lockPending(requestId);
        audit.record(approverId, AuditTargetType.ACCOUNT, account.getLoginId(), "가입 신청", "승인 대기",
                "거절 (사유: " + reason + ")");
        events.publishEvent(new SignupDecidedEvent(SignupDecidedEvent.Outcome.REJECTED, account.getId(),
                account.getLoginId(), account.getName(), null, account.getTelegramChatId(), reason));
        accounts.delete(account);
    }

    /** 승인 대기 신청 한 건을 잠그고 읽는다. 없거나 이미 처리된 신청이면 거부한다. */
    private AccountEntity lockPending(long requestId) {
        AccountEntity account = accounts.findForUpdate(requestId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        if (!account.isPending()) {
            throw new DomainException(HttpStatus.CONFLICT, "SIGNUP_REQUEST_STATE_CONFLICT",
                    "이미 처리된 가입 신청입니다.");
        }
        return account;
    }

    private static PendingSignup toPending(AccountEntity account) {
        return new PendingSignup(account.getId(), account.getLoginId(), account.getName(),
                AccountStatus.PENDING_APPROVAL, KstTime.toInstant(account.getCreatedAt()),
                account.isTelegramConnected());
    }
}

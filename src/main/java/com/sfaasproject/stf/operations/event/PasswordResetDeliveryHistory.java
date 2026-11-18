package com.sfaasproject.stf.operations.event;

import com.sfaasproject.stf.operations.port.BPorts.EventClaims;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Records actual send attempts even when the password reset transaction rolls back. */
@Component
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class PasswordResetDeliveryHistory {
    public static final String REDACTED_MESSAGE = "임시 비밀번호 발송 결과(비밀정보 제외)";
    public static final String FAILURE_REASON = "텔레그램 메시지를 보내지 못했습니다. 봇 연결 상태를 확인해 주세요.";
    private final JdbcTemplate jdbc;
    private final BEventPublisher events;
    private final EventClaims claims;
    private final TransactionTemplate newTx;

    public PasswordResetDeliveryHistory(JdbcTemplate jdbc, BEventPublisher events,
            EventClaims claims, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.events = events;
        this.claims = claims;
        this.newTx = new TransactionTemplate(manager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void afterCompletion(PasswordResetDeliveryRecorded result) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Password reset delivery requires an active transaction");
        // Only this redacted record is captured. Waiting until completion releases account/FK locks.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                try {
                    newTx.execute(ignored -> { events.publish(result); return null; });
                } catch (RuntimeException failure) {
                    // Exception text may contain SQL/credentials. Never log the exception or message body.
                    LoggerFactory.getLogger(PasswordResetDeliveryHistory.class)
                            .error("Password reset delivery metadata could not be queued: {}", result.eventId());
                }
            }
        });
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(PasswordResetDeliveryRecorded result) {
        if (!claims.claim("password-reset:" + result.eventId())) return;
        jdbc.update("INSERT INTO notification_log(account_id,receiver_name,notify_type,message,"
                        + "send_result,fail_reason,retry_count,sent_at) VALUES (?,?,?,?,?,?,?,?)",
                result.accountId(), result.receiverName(), "PASSWORD_RESET", REDACTED_MESSAGE,
                result.successful() ? "SUCCESS" : "FAILURE", result.successful() ? null : FAILURE_REASON,
                result.retryCount(), com.sfaasproject.stf.operations.adapter.JdbcBDatabase.time(result.attemptedAt()));
    }
}

package com.sfaasproject.stf.operations.event;

import com.sfaasproject.stf.operations.port.BPorts.EventClaims;
import com.sfaasproject.stf.telegram.port.NotificationPort;
import com.sfaasproject.stf.user.event.SignupDecidedEvent;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Stores A's decision in the same transaction, then delivers through B after commit. */
public class SignupNotificationBridge {
    private final BEventPublisher events;
    private final EventClaims claims;
    private final NotificationPort notifications;

    public SignupNotificationBridge(BEventPublisher events, EventClaims claims,
            NotificationPort notifications) {
        this.events = events;
        this.claims = claims;
        this.notifications = notifications;
    }

    @EventListener
    public void queue(SignupDecidedEvent event) {
        if (event.telegramChatId() == null || event.telegramChatId().isBlank()) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Signup decision requires an active transaction");
        boolean approved = event.outcome() == SignupDecidedEvent.Outcome.APPROVED;
        String message = approved
                ? event.name() + "님, 설비통합관제 가입이 승인되었습니다. 로그인해 주세요."
                : event.name() + "님, 설비통합관제 가입 신청이 거절되었습니다. 사유: " + event.reason();
        events.publish(new SignupNotificationRequested(UUID.randomUUID().toString(),
                approved ? event.accountId() : null, event.name(), event.telegramChatId(), message));
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(SignupNotificationRequested event) {
        if (!claims.claim("signup-decision:" + event.eventId())) return;
        notifications.deliver(new NotificationPort.Recipient(event.userId(), event.name(), event.chatId()),
                null, "SIGNUP_RESULT", event.message());
    }
}

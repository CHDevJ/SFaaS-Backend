package com.sfaasproject.stf.operations.adapter;

import com.sfaasproject.stf.operations.event.BEvents;
import com.sfaasproject.stf.operations.event.PasswordResetDeliveryHistory;
import com.sfaasproject.stf.operations.event.PasswordResetDeliveryRecorded;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;
import com.sfaasproject.stf.user.service.TemporaryPasswordSender;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Overrides A's standalone sender only when B is enabled. Plaintext exists only during the send. */
@Component
@Primary
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class BAuditedTemporaryPasswordSender implements TemporaryPasswordSender {
    private final JdbcTemplate jdbc;
    private final TelegramDeliveryService delivery;
    private final PasswordResetDeliveryHistory history;
    private final Clock clock;

    public BAuditedTemporaryPasswordSender(JdbcTemplate jdbc, TelegramDeliveryService delivery,
            PasswordResetDeliveryHistory history, Clock clock) {
        this.jdbc = jdbc;
        this.delivery = delivery;
        this.history = history;
        this.clock = clock;
    }

    @Override public boolean send(String chatId, String message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Password reset delivery requires an active transaction");
        // telegram_chat_id is unique; A already holds this account's update lock.
        var receiver = jdbc.queryForMap("SELECT account_id,name FROM account WHERE telegram_chat_id=?"
                + " AND deleted_at IS NULL AND approval_status='APPROVED'", chatId);
        var attemptedAt = OffsetDateTime.now(clock);
        TelegramDeliveryService.DeliveryResult result;
        try {
            result = delivery.sendWithOneRetry(chatId, message);
        } catch (RuntimeException failure) {
            // Never retain provider exception messages: they can contain the temporary password.
            result = new TelegramDeliveryService.DeliveryResult(false, 0, null);
        }
        history.afterCompletion(new PasswordResetDeliveryRecorded(BEvents.id(),
                ((Number) receiver.get("account_id")).longValue(), receiver.get("name").toString(),
                result.successful(), result.retryCount(), attemptedAt));
        return result.successful();
    }
}

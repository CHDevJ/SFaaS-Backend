package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.telegram.port.NotificationPort;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;

public class JdbcNotificationAdapter implements NotificationPort {
    private final JdbcBDatabase db;
    private final TelegramDeliveryService delivery;

    public JdbcNotificationAdapter(JdbcBDatabase db, TelegramDeliveryService delivery) {
        this.db = db;
        this.delivery = delivery;
    }

    @Override
    public TelegramDeliveryService.DeliveryResult deliver(
            Recipient recipient, Long alarmId, String type, String message) {
        if (recipient == null || recipient.name() == null || recipient.chatId() == null)
            throw new IllegalArgumentException("Recipient snapshot is required");
        var result = delivery.sendWithOneRetry(recipient.chatId(), message);
        db.update(
                "INSERT INTO"
                    + " notification_log(alarm_id,account_id,receiver_name,notify_type,message,send_result,fail_reason,retry_count,sent_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)",
                alarmId,
                recipient.userId(),
                recipient.name(),
                type,
                message,
                result.successful() ? "SUCCESS" : "FAILURE",
                result.failureReason(),
                result.retryCount(),
                time(db.now()));
        return result;
    }
}

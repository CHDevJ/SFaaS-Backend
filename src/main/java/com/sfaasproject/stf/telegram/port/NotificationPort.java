package com.sfaasproject.stf.telegram.port;

import com.sfaasproject.stf.telegram.service.TelegramDeliveryService.DeliveryResult;

/** A may snapshot an applicant before rejection/deletion and deliver after commit. */
public interface NotificationPort {
    record Recipient(Long userId, String name, String chatId) {}

    DeliveryResult deliver(Recipient recipient, Long alarmId, String type, String message);
}

package com.sfaasproject.stf.system.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** OpenAPI v5 judgement and calculation setting validation. */
@Service
public class SystemSettingsValidationService {
    public record Judgement(int communicationLossSeconds, int unacknowledgedReminderMinutes,
            int effectVerificationTargetCount, int alarmConsecutiveCount,
            int normalConsecutiveCount, int inventoryWarningMinutes,
            int inventoryDangerMinutes) { }

    public Judgement validate(Judgement settings) {
        if (settings == null) throw new IllegalArgumentException("Judgement settings are required");
        range("communicationLossSeconds", settings.communicationLossSeconds(), 10, 300);
        range("unacknowledgedReminderMinutes", settings.unacknowledgedReminderMinutes(), 1, 60);
        range("effectVerificationTargetCount", settings.effectVerificationTargetCount(), 5, 100);
        range("alarmConsecutiveCount", settings.alarmConsecutiveCount(), 1, 10);
        range("normalConsecutiveCount", settings.normalConsecutiveCount(), 1, 10);
        range("inventoryWarningMinutes", settings.inventoryWarningMinutes(), 11, 120);
        range("inventoryDangerMinutes", settings.inventoryDangerMinutes(), 1, 10);
        if (settings.inventoryDangerMinutes() >= settings.inventoryWarningMinutes()) {
            throw new IllegalArgumentException("Inventory danger must precede warning");
        }
        return settings;
    }

    public BigDecimal validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price must be non-negative");
        }
        return unitPrice;
    }

    private void range(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }
}

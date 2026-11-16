package com.sfaasproject.stf.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** SF5 inventory calculation from a 10-minute production/consumption window. */
@Service
public class InventoryForecastService {
    public enum Stage { MAINTAIN, CAUTION, DANGER }
    public record Forecast(int quantity, BigDecimal productionPerMinute,
            BigDecimal consumptionPerMinute, BigDecimal netConsumptionPerMinute,
            BigDecimal remainingMinutes, LocalDateTime expectedDepletionAt, Stage stage) { }

    public int nextQuantity(int previous, int produced, int consumed) {
        if (previous < 0 || produced < 0 || consumed < 0) {
            throw new IllegalArgumentException("Inventory quantities cannot be negative");
        }
        return Math.max(0, Math.subtractExact(Math.addExact(previous, produced), consumed));
    }

    public Forecast forecast(int quantity, long producedInTenMinutes,
            long consumedInTenMinutes, LocalDateTime now,
            int cautionMinutes, int dangerMinutes) {
        if (quantity < 0 || producedInTenMinutes < 0 || consumedInTenMinutes < 0
                || now == null || dangerMinutes < 0 || cautionMinutes <= dangerMinutes) {
            throw new IllegalArgumentException("Invalid inventory inputs or stage limits");
        }
        BigDecimal productionRate = BigDecimal.valueOf(producedInTenMinutes)
                .divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
        BigDecimal consumptionRate = BigDecimal.valueOf(consumedInTenMinutes)
                .divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
        BigDecimal net = consumptionRate.subtract(productionRate);
        if (net.signum() <= 0) {
            return new Forecast(quantity, productionRate, consumptionRate, net,
                    null, null, Stage.MAINTAIN);
        }
        BigDecimal remaining = BigDecimal.valueOf(quantity)
                .divide(net, 4, RoundingMode.HALF_UP);
        long remainingSeconds = remaining.multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.CEILING).longValueExact();
        Stage stage = remaining.compareTo(BigDecimal.valueOf(dangerMinutes)) <= 0
                ? Stage.DANGER : remaining.compareTo(BigDecimal.valueOf(cautionMinutes)) <= 0
                        ? Stage.CAUTION : Stage.MAINTAIN;
        return new Forecast(quantity, productionRate, consumptionRate, net,
                remaining, now.plusSeconds(remainingSeconds), stage);
    }
}

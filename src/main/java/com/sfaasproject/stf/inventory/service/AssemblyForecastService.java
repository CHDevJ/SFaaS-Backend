package com.sfaasproject.stf.inventory.service;

import com.sfaasproject.stf.operations.dto.V7Contract.*;

import org.springframework.stereotype.Service;

import java.math.*;
import java.time.*;

/** v7 stock snapshot; amount is rounded only once in won, never via rounded display minutes. */
@Service
public class AssemblyForecastService {
    public record Input(
            Long quantity,
            BigDecimal production,
            BigDecimal consumption,
            OffsetDateTime calculatedAt,
            String calculationStatus) {}

    public record Result(
            BigDecimal netConsumption,
            BigDecimal remainingMinutes,
            OffsetDateTime stopAt,
            String stage,
            BigDecimal marginMinutes,
            BigDecimal shortageMinutes,
            BigDecimal loss,
            String forecastStatus) {}

    public Result calculate(
            Input i,
            int warning,
            int danger,
            OffsetDateTime recovery,
            BigDecimal hourly,
            BigDecimal price) {
        if (i == null
                || i.calculatedAt() == null
                || warning < 5
                || warning > 120
                || danger < 1
                || danger > 60
                || danger >= warning)
            throw new IllegalArgumentException("Invalid forecast thresholds/snapshot");
        if (!"READY".equals(i.calculationStatus())
                || i.quantity() == null
                || i.production() == null
                || i.consumption() == null
                || hourly == null
                || hourly.signum() <= 0
                || price == null
                || price.signum() < 0)
            return new Result(null, null, null, null, null, null, null, "INSUFFICIENT_DATA");
        if (i.quantity() < 0 || i.production().signum() < 0 || i.consumption().signum() < 0)
            throw new IllegalArgumentException("Negative forecast input");
        var net = i.consumption().subtract(i.production());
        if (net.signum() <= 0)
            return new Result(net, null, null, "MAINTAIN", null, null, null, "NO_DEPLETION");
        var remaining = BigDecimal.valueOf(i.quantity()).divide(net, 12, RoundingMode.HALF_UP);
        var stop =
                i.calculatedAt()
                        .plusNanos(
                                BigDecimal.valueOf(i.quantity())
                                        .multiply(BigDecimal.valueOf(60_000_000_000L))
                                        .divide(net, 0, RoundingMode.CEILING)
                                        .longValueExact());
        String stage =
                BigDecimal.valueOf(i.quantity()).compareTo(net.multiply(BigDecimal.valueOf(danger)))
                                <= 0
                        ? "DANGER"
                        : BigDecimal.valueOf(i.quantity())
                                                .compareTo(
                                                        net.multiply(BigDecimal.valueOf(warning)))
                                        <= 0
                                ? "CAUTION"
                                : "MAINTAIN";
        if (recovery == null)
            return new Result(net, remaining, stop, stage, null, null, null, "NO_RECOVERY_HISTORY");
        var delta = BigDecimal.valueOf(Duration.between(stop, recovery).toNanos());
        var shortage =
                delta.max(BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(60_000_000_000L), 12, RoundingMode.HALF_UP);
        var margin =
                delta.negate()
                        .divide(BigDecimal.valueOf(60_000_000_000L), 12, RoundingMode.HALF_UP);
        var loss =
                delta.max(BigDecimal.ZERO)
                        .multiply(hourly)
                        .multiply(price)
                        .divide(BigDecimal.valueOf(3_600_000_000_000L), 0, RoundingMode.HALF_UP);
        return new Result(net, remaining, stop, stage, margin, shortage, loss, "READY");
    }
}

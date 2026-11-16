package com.sfaasproject.stf.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Assembly stop, recovery margin and estimated loss from supplied line forecasts. */
@Service
public class AssemblyLossDecisionService {
    public record LineForecast(long lineId, LocalDateTime depletionAt) { }
    public record Forecast(Long limitingLineId, LocalDateTime stopExpectedAt,
            LocalDateTime recoveryExpectedAt, BigDecimal marginMinutes,
            BigDecimal estimatedLoss) { }

    public Forecast forecast(List<LineForecast> lines, LocalDateTime now,
            long estimatedRepairMinutes, BigDecimal hourlyOutput, BigDecimal unitPrice) {
        if (lines == null || now == null || estimatedRepairMinutes < 0 || hourlyOutput == null
                || hourlyOutput.signum() < 0 || unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Invalid assembly forecast inputs");
        }
        LocalDateTime recovery = now.plusMinutes(estimatedRepairMinutes);
        LineForecast limiting = lines.stream().filter(l -> l.depletionAt() != null)
                .min(Comparator.comparing(LineForecast::depletionAt)).orElse(null);
        if (limiting == null) return new Forecast(null, null, recovery, null, BigDecimal.ZERO);
        BigDecimal margin = BigDecimal.valueOf(Duration.between(recovery,
                limiting.depletionAt()).toSeconds()).divide(BigDecimal.valueOf(60), 2,
                RoundingMode.HALF_UP);
        long lossSeconds = Math.max(0, Duration.between(limiting.depletionAt(), recovery).toSeconds());
        BigDecimal loss = BigDecimal.valueOf(lossSeconds).multiply(hourlyOutput)
                .multiply(unitPrice).divide(BigDecimal.valueOf(3600), 0, RoundingMode.HALF_UP);
        return new Forecast(limiting.lineId(), limiting.depletionAt(), recovery, margin, loss);
    }

    public BigDecimal avoidedLoss(long recoveredBeforeStopCount, long averageDowntimeMinutes,
            BigDecimal hourlyOutput, BigDecimal unitPrice) {
        if (recoveredBeforeStopCount < 0 || averageDowntimeMinutes < 0 || hourlyOutput == null
                || hourlyOutput.signum() < 0 || unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Invalid avoided-loss inputs");
        }
        return BigDecimal.valueOf(recoveredBeforeStopCount)
                .multiply(BigDecimal.valueOf(averageDowntimeMinutes))
                .multiply(hourlyOutput).multiply(unitPrice)
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
    }
}

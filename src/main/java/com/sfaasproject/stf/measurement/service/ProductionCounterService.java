package com.sfaasproject.stf.measurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.stereotype.Service;

/** Delta of a cumulative production counter; first observation establishes the baseline. */
@Service
public class ProductionCounterService {
    public int quantity(BigDecimal previous, LocalDateTime previousAt, BigDecimal current,
            LocalDateTime currentAt, LocalTime resetTime) {
        if (current == null || current.signum() < 0 || current.stripTrailingZeros().scale() > 0
                || currentAt == null) {
            throw new IllegalArgumentException("Production counter must be a nonnegative integer with a timestamp");
        }
        if (previous == null) return 0;
        if (previousAt == null || !currentAt.isAfter(previousAt)
                || previous.signum() < 0 || previous.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Invalid previous production sample");
        }
        if (resetTime != null && crossedReset(previousAt, currentAt, resetTime)) {
            return 0; // A new epoch starts here; the next valid sample contributes its delta.
        }
        if (current.compareTo(previous) >= 0) {
            return current.subtract(previous).intValueExact();
        }
        // 초기화 시각 밖에서 줄어든 카운터는 PLC·Node-RED 재시작 같은 리셋이다(API_SPEC 4장: 카운터 리셋은 생산으로
        // 합산하지 않는다). 메시지 전체를 거절하면 같은 묶음의 가동 신호·센서 값까지 버려져 설비가 통신 끊김으로 보이므로,
        // 여기서는 새 기준점으로 보고 0 을 돌려준다. 저장 쪽(JdbcV7IngestionStore)이 COUNTER_RESET_PENDING 으로 남긴다.
        return 0;
    }

    private boolean crossedReset(LocalDateTime before, LocalDateTime after, LocalTime resetTime) {
        LocalDate date = before.toLocalDate();
        LocalDateTime reset = date.atTime(resetTime);
        if (!reset.isAfter(before)) reset = reset.plusDays(1);
        return !reset.isAfter(after);
    }
}

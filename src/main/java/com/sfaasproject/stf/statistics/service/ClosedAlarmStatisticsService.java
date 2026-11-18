package com.sfaasproject.stf.statistics.service;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.alarm.service.AlarmLifecycleService.ProcessStatus;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/** v6 response metrics and equipment-fault MTTR/MTBF; never invent zero for no samples. */
@Service
public class ClosedAlarmStatisticsService {
    public record Alarm(
            long alarmId,
            long equipmentId,
            AlarmGrade grade,
            ProcessStatus processStatus,
            OffsetDateTime occurredAt,
            OffsetDateTime closedAt) {}

    public record Response(
            int activeCautionCount,
            int activeCriticalCount,
            Long longestActionEquipmentId,
            int totalCount,
            int cautionCount,
            int criticalCount,
            int activeCount,
            BigDecimal averageActionMinutes,
            BigDecimal longestActionMinutes) {}

    public record Reliability(int faultCount, BigDecimal mttrMinutes, BigDecimal mtbfMinutes) {}

    public Response response(List<Alarm> alarms, OffsetDateTime from, OffsetDateTime to) {
        var selected = select(alarms, from, to);
        var durations = selected.stream().filter(this::closed).map(this::durationMinutes).toList();
        int critical =
                (int) selected.stream().filter(a -> a.grade() == AlarmGrade.CRITICAL).count();
        var active =
                selected.stream().filter(a -> a.processStatus() == ProcessStatus.ACTIVE).toList();
        var longest =
                selected.stream()
                        .filter(this::closed)
                        .sorted(
                                Comparator.comparing(this::durationMinutes)
                                        .reversed()
                                        .thenComparingLong(Alarm::alarmId))
                        .findFirst()
                        .orElse(null);
        return new Response(
                (int) active.stream().filter(a -> a.grade() == AlarmGrade.CAUTION).count(),
                (int) active.stream().filter(a -> a.grade() == AlarmGrade.CRITICAL).count(),
                longest == null ? null : longest.equipmentId(),
                selected.size(),
                selected.size() - critical,
                critical,
                (int)
                        selected.stream()
                                .filter(a -> a.processStatus() == ProcessStatus.ACTIVE)
                                .count(),
                average(durations),
                durations.stream().max(Comparator.naturalOrder()).orElse(null));
    }

    public Reliability reliability(
            long equipmentId, List<Alarm> alarms, OffsetDateTime from, OffsetDateTime to) {
        if (equipmentId <= 0) throw new IllegalArgumentException("Equipment ID must be positive");
        var faults =
                select(alarms, from, to).stream()
                        .filter(
                                a ->
                                        a.equipmentId() == equipmentId
                                                && a.grade() == AlarmGrade.CRITICAL
                                                && closed(a))
                        .sorted(
                                Comparator.comparing(Alarm::occurredAt)
                                        .thenComparingLong(Alarm::alarmId))
                        .toList();
        var gaps = new java.util.ArrayList<BigDecimal>();
        for (int i = 1; i < faults.size(); i++)
            gaps.add(
                    minutes(
                            Duration.between(
                                    faults.get(i - 1).occurredAt(), faults.get(i).occurredAt())));
        return new Reliability(
                faults.size(),
                average(faults.stream().map(this::durationMinutes).toList()),
                average(gaps));
    }

    private List<Alarm> select(List<Alarm> alarms, OffsetDateTime from, OffsetDateTime to) {
        if (alarms == null || from == null || to == null || !to.isAfter(from))
            throw new IllegalArgumentException("A nonempty half-open query period is required");
        for (var a : alarms) {
            if (a == null
                    || a.alarmId() <= 0
                    || a.equipmentId() <= 0
                    || a.grade() == null
                    || a.processStatus() == null
                    || a.occurredAt() == null
                    || a.closedAt() != null && a.closedAt().isBefore(a.occurredAt())
                    || a.processStatus() == ProcessStatus.CLOSED && a.closedAt() == null)
                throw new IllegalArgumentException("Invalid alarm statistics record");
        }
        return alarms.stream()
                .filter(a -> !a.occurredAt().isBefore(from) && a.occurredAt().isBefore(to))
                .toList();
    }

    private boolean closed(Alarm alarm) {
        return alarm.processStatus() == ProcessStatus.CLOSED;
    }

    private BigDecimal durationMinutes(Alarm alarm) {
        return minutes(Duration.between(alarm.occurredAt(), alarm.closedAt()));
    }

    private BigDecimal minutes(Duration duration) {
        return BigDecimal.valueOf(duration.toNanos(), 9)
                .divide(BigDecimal.valueOf(60), 12, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        return values.isEmpty()
                ? null
                : values.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 12, RoundingMode.HALF_UP);
    }
}

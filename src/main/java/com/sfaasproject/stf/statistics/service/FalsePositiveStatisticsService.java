package com.sfaasproject.stf.statistics.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** False-positive summary for records already filtered by period and line. */
@Service
public class FalsePositiveStatisticsService {
    public record Alarm(long equipmentId, String equipmentName, long dataItemId,
            String dataItemName, LocalDateTime occurredAt, LocalDateTime acknowledgedAt,
            boolean falsePositive) { }
    public record NamedCount(long id, String name, int count) { }
    public record NamedRate(long id, String name, double rate) { }
    public record Summary(int count, double rate, double responseMinutes,
            List<NamedCount> equipmentRanking, List<NamedRate> dataItemRates) { }

    public Summary summarize(List<Alarm> alarms) {
        if (alarms == null) throw new IllegalArgumentException("Alarms are required");
        for (Alarm alarm : alarms) validate(alarm);
        List<Alarm> falseAlarms = alarms.stream().filter(Alarm::falsePositive).toList();
        Map<Long, List<Alarm>> byEquipment = falseAlarms.stream()
                .collect(Collectors.groupingBy(Alarm::equipmentId));
        List<NamedCount> equipment = byEquipment.entrySet().stream().map(entry ->
                new NamedCount(entry.getKey(), entry.getValue().get(0).equipmentName(),
                        entry.getValue().size()))
                .sorted(Comparator.comparingInt(NamedCount::count).reversed()
                        .thenComparingLong(NamedCount::id)).toList();
        Map<Long, List<Alarm>> byItem = alarms.stream()
                .collect(Collectors.groupingBy(Alarm::dataItemId));
        List<NamedRate> items = byItem.entrySet().stream().map(entry -> {
            List<Alarm> group = entry.getValue();
            long falseCount = group.stream().filter(Alarm::falsePositive).count();
            return new NamedRate(entry.getKey(), group.get(0).dataItemName(),
                    100.0 * falseCount / group.size());
        }).sorted(Comparator.comparingDouble(NamedRate::rate).reversed()
                .thenComparingLong(NamedRate::id)).toList();
        double responseSeconds = falseAlarms.stream().filter(a -> a.acknowledgedAt() != null)
                .mapToLong(a -> Duration.between(a.occurredAt(), a.acknowledgedAt()).toSeconds())
                .average().orElse(0);
        return new Summary(falseAlarms.size(),
                alarms.isEmpty() ? 0 : 100.0 * falseAlarms.size() / alarms.size(),
                responseSeconds / 60.0, equipment, items);
    }

    private void validate(Alarm alarm) {
        if (alarm == null || alarm.equipmentId() <= 0 || alarm.dataItemId() <= 0
                || alarm.equipmentName() == null || alarm.dataItemName() == null
                || alarm.occurredAt() == null
                || (alarm.acknowledgedAt() != null
                    && alarm.acknowledgedAt().isBefore(alarm.occurredAt()))) {
            throw new IllegalArgumentException("Invalid alarm statistics record");
        }
    }
}

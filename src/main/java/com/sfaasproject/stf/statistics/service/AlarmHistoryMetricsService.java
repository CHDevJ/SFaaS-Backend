package com.sfaasproject.stf.statistics.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** Aggregates already filtered alarm records; persistence supplies the period and line. */
@Service
public class AlarmHistoryMetricsService {
    public record Alarm(LocalDateTime occurredAt, LocalDateTime acknowledgedAt,
            LocalDateTime repairedAt, boolean delayed, boolean falsePositive) { }
    public record Response(double averageAcknowledgementMinutes, double averageRepairMinutes,
            int delayedAlarmCount, double delayedAlarmRate) { }

    public Response response(List<Alarm> alarms) {
        if (alarms == null) throw new IllegalArgumentException("Alarms are required");
        double ackSum = 0;
        double repairSum = 0;
        int ackCount = 0;
        int repairCount = 0;
        int delayed = 0;
        for (Alarm alarm : alarms) {
            validate(alarm);
            if (alarm.acknowledgedAt() != null) {
                ackSum += seconds(alarm.occurredAt(), alarm.acknowledgedAt()) / 60.0;
                ackCount++;
            }
            if (alarm.repairedAt() != null && !alarm.falsePositive()) {
                repairSum += seconds(alarm.occurredAt(), alarm.repairedAt()) / 60.0;
                repairCount++;
            }
            if (alarm.delayed()) delayed++;
        }
        return new Response(ackCount == 0 ? 0 : ackSum / ackCount,
                repairCount == 0 ? 0 : repairSum / repairCount, delayed,
                alarms.isEmpty() ? 0 : delayed * 100.0 / alarms.size());
    }

    private void validate(Alarm alarm) {
        if (alarm == null || alarm.occurredAt() == null) {
            throw new IllegalArgumentException("Alarm occurrence is required");
        }
        if (alarm.acknowledgedAt() != null) seconds(alarm.occurredAt(), alarm.acknowledgedAt());
        if (alarm.repairedAt() != null) seconds(alarm.occurredAt(), alarm.repairedAt());
    }

    private long seconds(LocalDateTime start, LocalDateTime end) {
        long result = Duration.between(start, end).getSeconds();
        if (result < 0) throw new IllegalArgumentException("Alarm time precedes occurrence");
        return result;
    }
}

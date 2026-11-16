package com.sfaasproject.stf.alarm.service;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.ProcessStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Default order and derived display fields for ALM-001. */
@Service
public class AlarmListDecisionService {
    public record Entry(long alarmId, AlarmGrade grade, ProcessStatus processStatus,
            LocalDateTime occurredAt) { }
    public record Display(Entry alarm, long elapsedSeconds, boolean acknowledgementDelayed) { }

    public List<Display> order(List<Entry> alarms, LocalDateTime now) {
        Objects.requireNonNull(alarms);
        Objects.requireNonNull(now);
        return alarms.stream()
                .filter(a -> a.processStatus() != ProcessStatus.CLOSED)
                .sorted(Comparator
                        .comparingInt((Entry a) -> a.processStatus() == ProcessStatus.UNACKNOWLEDGED ? 0 : 1)
                        .thenComparingInt(a -> a.grade() == AlarmGrade.CRITICAL ? 0 : 1)
                        .thenComparing(Entry::occurredAt, Comparator.reverseOrder())
                        .thenComparingLong(Entry::alarmId))
                .map(a -> {
                    long elapsed = Math.max(0, Duration.between(a.occurredAt(), now).toSeconds());
                    return new Display(a, elapsed,
                            a.processStatus() == ProcessStatus.UNACKNOWLEDGED && elapsed >= 180);
                }).toList();
    }
}

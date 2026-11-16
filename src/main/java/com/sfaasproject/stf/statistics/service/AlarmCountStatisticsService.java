package com.sfaasproject.stf.statistics.service;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Date bucket and false-positive counts from source alarm records. */
@Service
public class AlarmCountStatisticsService {
    public record Alarm(LocalDateTime occurredAt, AlarmGrade grade, boolean falsePositive) { }
    public record Daily(LocalDate date, int criticalCount, int cautionCount,
            int falsePositiveCount) { }
    public record FalsePositiveSummary(int count, int total, double ratePercent) { }

    public List<Daily> daily(List<Alarm> alarms, LocalDate from, LocalDate to) {
        if (alarms == null || from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid alarm statistics period");
        }
        var result = new ArrayList<Daily>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            final LocalDate day = date;
            List<Alarm> matching = alarms.stream()
                    .filter(a -> a.occurredAt().toLocalDate().equals(day)).toList();
            result.add(new Daily(day,
                    (int) matching.stream().filter(a -> a.grade() == AlarmGrade.CRITICAL).count(),
                    (int) matching.stream().filter(a -> a.grade() == AlarmGrade.CAUTION).count(),
                    (int) matching.stream().filter(Alarm::falsePositive).count()));
        }
        return List.copyOf(result);
    }

    public FalsePositiveSummary falsePositives(List<Alarm> alarms) {
        if (alarms == null) throw new IllegalArgumentException("Alarms are required");
        int count = (int) alarms.stream().filter(Alarm::falsePositive).count();
        return new FalsePositiveSummary(count, alarms.size(),
                alarms.isEmpty() ? 0 : count * 100.0 / alarms.size());
    }
}

package com.sfaasproject.stf.duty.service;

import com.sfaasproject.stf.common.DomainException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Validates inclusive duty date ranges and builds a month calendar. */
@Service
public class DutyScheduleDecisionService {
    public record Schedule(long id, LocalDate startsOn, LocalDate endsOn, Set<Long> assigneeIds) { }
    public record CalendarDay(LocalDate date, Long scheduleId, Set<Long> assigneeIds) { }

    public void validate(List<Schedule> schedules, LocalDate requiredFrom,
            LocalDate requiredThrough, Set<Long> activeMaintenanceIds) {
        if (schedules == null || requiredFrom == null || requiredThrough == null
                || requiredFrom.isAfter(requiredThrough) || activeMaintenanceIds == null) {
            throw new IllegalArgumentException("Invalid duty coverage inputs");
        }
        List<Schedule> ordered = schedules.stream()
                .sorted(Comparator.comparing(Schedule::startsOn)).toList();
        for (Schedule schedule : ordered) {
            if (schedule.startsOn() == null || schedule.endsOn() == null
                    || schedule.endsOn().isBefore(schedule.startsOn())
                    || schedule.assigneeIds() == null || schedule.assigneeIds().isEmpty()
                    || !activeMaintenanceIds.containsAll(schedule.assigneeIds())) {
                throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "ASSIGNEE_NOT_USER", "사용 중인 보전 담당자를 선택해 주세요.");
            }
        }
        LocalDate coveredThrough = requiredFrom.minusDays(1);
        LocalDate previousEnd = null;
        for (Schedule schedule : ordered) {
            if (previousEnd != null && !schedule.startsOn().isAfter(previousEnd)) {
                throw new DomainException(HttpStatus.CONFLICT, "DUTY_OVERLAP",
                        "같은 기간에 이미 당번이 있습니다.");
            }
            if (schedule.startsOn().isAfter(coveredThrough.plusDays(1))
                    && !schedule.startsOn().isAfter(requiredThrough)) {
                throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "DUTY_GAP_NOT_ALLOWED",
                        "당번이 없는 기간을 만들 수 없습니다.");
            }
            if (schedule.endsOn().isAfter(coveredThrough)) coveredThrough = schedule.endsOn();
            previousEnd = schedule.endsOn();
        }
        if (coveredThrough.isBefore(requiredThrough)) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "DUTY_GAP_NOT_ALLOWED",
                    "당번이 없는 기간을 만들 수 없습니다.");
        }
    }

    public boolean canDeleteLastFutureSchedule(long scheduleId, LocalDate today,
            List<Schedule> schedules) {
        if (today == null || schedules == null) throw new IllegalArgumentException("Date and schedules are required");
        return schedules.stream().max(Comparator.comparing(Schedule::endsOn))
                .filter(s -> s.id() == scheduleId && s.startsOn().isAfter(today))
                .isPresent();
    }

    public List<CalendarDay> calendar(YearMonth month, List<Schedule> schedules) {
        if (month == null || schedules == null) throw new IllegalArgumentException("Month and schedules are required");
        var days = new ArrayList<CalendarDay>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            Schedule match = schedules.stream()
                    .filter(s -> !date.isBefore(s.startsOn()) && !date.isAfter(s.endsOn()))
                    .findFirst().orElse(null);
            days.add(new CalendarDay(date, match == null ? null : match.id(),
                    match == null ? Set.of() : match.assigneeIds()));
        }
        return List.copyOf(days);
    }
}

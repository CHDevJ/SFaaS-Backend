package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.duty.service.DutyScheduleDecisionService;
import com.sfaasproject.stf.duty.service.DutyScheduleDecisionService.Schedule;
import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;

import org.springframework.http.HttpStatus;

import java.time.*;
import java.util.*;

public class DutyApplicationService {
    private final Duties duties;
    private final Users users;
    private final UnitOfWork tx;
    private final DutyScheduleDecisionService rules;
    private final Clock clock;
    private final AuditRecorder audit;

    public DutyApplicationService(
            Duties duties,
            Users users,
            UnitOfWork tx,
            DutyScheduleDecisionService rules,
            Clock clock,
            AuditRecorder audit) {
        this.duties = duties;
        this.users = users;
        this.tx = tx;
        this.rules = rules;
        this.clock = clock;
        this.audit = audit;
    }

    public DutyScheduleListResponse getAlarmDutySchedules() {
        BAccess.current();
        return new DutyScheduleListResponse(
                true,
                duties.schedules().stream()
                        .filter(d -> !d.to().isBefore(LocalDate.now(clock)))
                        .map(this::dto)
                        .toList());
    }

    public DutyScheduleResponse getCurrentAlarmDutySchedule() {
        BAccess.current();
        LocalDate today = LocalDate.now(clock);
        var duty =
                duties.schedules().stream()
                        .filter(d -> !today.isBefore(d.from()) && !today.isAfter(d.to()))
                        .findFirst()
                        .orElseThrow(() -> Checks.missing("DUTY_SCHEDULE_NOT_FOUND"));
        return new DutyScheduleResponse(true, dto(duty));
    }

    public DutyScheduleResponse createAlarmDutySchedule(DutyScheduleWriteRequest request) {
        long actorId = BAccess.admin().userId();
        return tx.execute(
                () -> {
                    duties.lockAssignee(request.dutyUserId());
                    requireMaintenance(request.dutyUserId());
                    var list = new ArrayList<>(duties.lockedSchedules());
                    list.add(
                            new Duty(
                                    Long.MAX_VALUE,
                                    request.startDate(),
                                    request.endDate(),
                                    request.dutyUserId()));
                    validate(list);
                    var created = duties.insert(request.startDate(), request.endDate(), request.dutyUserId());
                    recordChange(actorId, created.id(), List.of(), List.of(created));
                    return new DutyScheduleResponse(true, dto(created));
                });
    }

    public DutyScheduleResponse updateAlarmDutySchedule(Long id, DutyScheduleWriteRequest request) {
        long actorId = BAccess.admin().userId();
        return tx.execute(
                () -> {
                    duties.lockAssignee(request.dutyUserId());
                    requireMaintenance(request.dutyUserId());
                    var list = new ArrayList<>(duties.lockedSchedules());
                    require(list, id);
                    var before = list.stream().filter(d -> d.id() == id).toList();
                    list.removeIf(d -> d.id() == id);
                    var changed =
                            new Duty(
                                    id,
                                    request.startDate(),
                                    request.endDate(),
                                    request.dutyUserId());
                    list.add(changed);
                    validate(list);
                    duties.update(changed);
                    recordChange(actorId, id, before, List.of(changed));
                    return new DutyScheduleResponse(true, dto(changed));
                });
    }

    public void deleteAlarmDutySchedule(Long id) {
        long actorId = BAccess.admin().userId();
        tx.execute(
                () -> {
                    var list = new ArrayList<>(duties.lockedSchedules());
                    require(list, id);
                    if (!rules.canDeleteLastFutureSchedule(
                            id, LocalDate.now(clock), schedules(list)))
                        throw new DomainException(
                                HttpStatus.UNPROCESSABLE_CONTENT,
                                "DUTY_GAP_NOT_ALLOWED",
                                "마지막 예정 당번만 삭제할 수 있습니다.");
                    var before = list.stream().filter(d -> d.id() == id).toList();
                    duties.delete(id);
                    recordChange(actorId, id, before, List.of());
                    return null;
                });
    }

    public DutyCalendarResponse getAlarmDutyCalendar(String month) {
        BAccess.current();
        YearMonth ym;
        try {
            if (month == null || !month.matches("^[0-9]{4}-(0[1-9]|1[0-2])$"))
                throw new IllegalArgumentException();
            ym = YearMonth.parse(month);
        } catch (Exception ex) {
            throw Checks.invalid("조회 월을 확인해 주세요.");
        }
        var all = duties.schedules();
        var result = new ArrayList<DutyCalendarDay>();
        for (int i = 1; i <= ym.lengthOfMonth(); i++) {
            LocalDate date = ym.atDay(i);
            result.add(
                    new DutyCalendarDay(
                            date,
                            all.stream()
                                    .filter(d -> !date.isBefore(d.from()) && !date.isAfter(d.to()))
                                    .map(this::dto)
                                    .toList()));
        }
        return new DutyCalendarResponse(true, result);
    }

    /** Field-level snapshots share the duty transaction; a failed audit rolls back the mutation. */
    private void recordChange(long actorId, long id, List<Duty> before, List<Duty> after) {
        recordField(actorId, id, "시작일", date(before, true), date(after, true));
        recordField(actorId, id, "종료일", date(before, false), date(after, false));
        recordField(actorId, id, "담당자", assignees(before), assignees(after));
    }

    private void recordField(long actorId, long id, String field, String before, String after) {
        if (!Objects.equals(before, after))
            audit.record(actorId, AuditTargetType.DUTY, "당번 " + id, field, before, after);
    }

    private String date(List<Duty> values, boolean start) {
        return values.isEmpty() ? null : (start ? values.getFirst().from() : values.getFirst().to()).toString();
    }

    private String assignees(List<Duty> values) {
        return values.isEmpty() ? null : values.stream().map(Duty::userId).distinct().sorted()
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private void requireMaintenance(long userId) {
        if (!users.maintenanceIds().contains(userId))
            throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNEE_NOT_MAINTENANCE",
                    "사용 중인 보전 담당자를 선택해 주세요.");
    }

    private void validate(List<Duty> values) {
        var today = LocalDate.now(clock);
        var active = users.maintenanceIds();
        if (values.stream().anyMatch(d -> !d.to().isBefore(today) && !active.contains(d.userId())))
            throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNEE_NOT_MAINTENANCE",
                    "사용 중인 보전 담당자를 선택해 주세요.");
        var from = values.stream().map(Duty::from).min(LocalDate::compareTo).orElseThrow();
        var to = values.stream().map(Duty::to).max(LocalDate::compareTo).orElseThrow();
        // Completed periods retain historical users, including deleted or reclassified accounts.
        var historicalIds = values.stream().map(Duty::userId).collect(java.util.stream.Collectors.toSet());
        rules.validate(schedules(values), from, to, historicalIds);
    }

    private List<Schedule> schedules(List<Duty> values) {
        var grouped = new LinkedHashMap<Long, Set<Long>>();
        values.forEach(d -> grouped.computeIfAbsent(d.id(), ignored -> new HashSet<>()).add(d.userId()));
        return values.stream().collect(java.util.stream.Collectors.toMap(Duty::id, d -> d,
                (first, ignored) -> first, LinkedHashMap::new)).values().stream()
                .map(d -> new Schedule(d.id(), d.from(), d.to(), grouped.get(d.id())))
                .toList();
    }

    private void require(List<Duty> list, Long id) {
        Checks.required(id, "scheduleId");
        Checks.positive(id, "scheduleId");
        if (list.stream().noneMatch(d -> d.id() == id))
            throw Checks.missing("DUTY_SCHEDULE_NOT_FOUND");
    }

    private DutySchedule dto(Duty duty) {
        var today = LocalDate.now(clock);
        var user = duty.to().isBefore(today) ? users.historical(duty.userId()) : users.active(duty.userId());
        if (user == null) throw Checks.missing("USER_NOT_FOUND");
        return new DutySchedule(
                duty.id(),
                duty.from(),
                duty.to(),
                user,
                today.isBefore(duty.from())
                        ? DutyScheduleStatus.UPCOMING
                        : DutyScheduleStatus.CURRENT);
    }
}

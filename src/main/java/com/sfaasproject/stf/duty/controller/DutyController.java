package com.sfaasproject.stf.duty.controller;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BReadPort.*;
import com.sfaasproject.stf.operations.service.*;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class DutyController {
    private final DutyApplicationService commands;

    public DutyController(DutyApplicationService commands) {
        this.commands = commands;
    }

    @GetMapping("/system/alarm-duty-schedules")
    public DutyScheduleListResponse getAlarmDutySchedules() {
        BAccess.current();
        return commands.getAlarmDutySchedules();
    }

    @PostMapping("/system/alarm-duty-schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public DutyScheduleResponse createAlarmDutySchedule(
            @Valid @RequestBody DutyScheduleWriteRequest request) {
        BAccess.admin();
        return commands.createAlarmDutySchedule(request);
    }

    @GetMapping("/system/alarm-duty-schedules/current")
    public DutyScheduleResponse getCurrentAlarmDutySchedule() {
        BAccess.current();
        return commands.getCurrentAlarmDutySchedule();
    }

    @GetMapping("/alarm-duty-schedules/calendar")
    public DutyCalendarResponse getAlarmDutyCalendar(
            @RequestParam(name = "month", required = true) String month) {
        BAccess.current();
        return commands.getAlarmDutyCalendar(month);
    }

    @PutMapping("/system/alarm-duty-schedules/{scheduleId}")
    public DutyScheduleResponse updateAlarmDutySchedule(
            @PathVariable("scheduleId") Long scheduleId,
            @Valid @RequestBody DutyScheduleWriteRequest request) {
        BAccess.admin();
        return commands.updateAlarmDutySchedule(scheduleId, request);
    }

    @DeleteMapping("/system/alarm-duty-schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlarmDutySchedule(@PathVariable("scheduleId") Long scheduleId) {
        BAccess.admin();
        commands.deleteAlarmDutySchedule(scheduleId);
    }
}

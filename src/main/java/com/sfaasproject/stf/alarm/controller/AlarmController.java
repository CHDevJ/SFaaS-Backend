package com.sfaasproject.stf.alarm.controller;

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
public class AlarmController {
    private final BReadService reads;
    private final AlarmApplicationService commands;

    public AlarmController(BReadService reads, AlarmApplicationService commands) {
        this.reads = reads;
        this.commands = commands;
    }

    @GetMapping("/alarms")
    public AlarmPageResponse getActiveAlarms(
            @RequestParam(name = "lineId", required = false) Long lineId,
            @RequestParam(name = "equipmentId", required = false) Long equipmentId,
            @RequestParam(name = "processStatus", required = false)
                    AlarmProcessStatus processStatus,
            @RequestParam(name = "grade", required = false) AlarmGrade grade,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "sort", required = false) String sort) {
        BAccess.current();
        return reads.getActiveAlarms(
                new GetActiveAlarmsQuery(
                        lineId, equipmentId, processStatus, grade, page, size, sort));
    }

    @GetMapping("/alarms/recent-closed")
    public RecentClosedAlarmResponse getRecentClosedAlarms(
            @RequestParam(name = "limit", required = false, defaultValue = "20") Integer limit) {
        BAccess.current();
        return reads.getRecentClosedAlarms(new GetRecentClosedAlarmsQuery(limit));
    }

    @GetMapping("/alarms/{alarmId}")
    public AlarmDetailResponse getAlarmDetail(@PathVariable("alarmId") Long alarmId) {
        BAccess.current();
        return reads.getAlarmDetail(new GetAlarmDetailQuery(alarmId));
    }

    @GetMapping("/alarms/{alarmId}/comparisons")
    public AlarmComparisonResponse getAlarmComparison(@PathVariable("alarmId") Long alarmId) {
        BAccess.current();
        return reads.getAlarmComparison(new GetAlarmComparisonQuery(alarmId));
    }

    @PatchMapping("/alarms/{alarmId}/acknowledgement")
    public AlarmAcknowledgementResponse acknowledgeAlarm(
            @PathVariable("alarmId") Long alarmId,
            @Valid @RequestBody AlarmAcknowledgementRequest request) {
        BAccess.current();
        return commands.acknowledgeAlarm(alarmId, request);
    }

    @GetMapping("/alarms/{alarmId}/recommended-actions")
    public RecommendedActionResponse getRecommendedActions(@PathVariable("alarmId") Long alarmId) {
        BAccess.current();
        return reads.getRecommendedActions(new GetRecommendedActionsQuery(alarmId));
    }

    @PostMapping("/alarms/{alarmId}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public AlarmActionResponse createAlarmAction(
            @PathVariable("alarmId") Long alarmId, @Valid @RequestBody AlarmActionRequest request) {
        BAccess.current();
        return commands.createAlarmAction(alarmId, request);
    }

    @PostMapping("/alarms/{alarmId}/false-positive")
    public AlarmClosureResponse reportFalsePositive(
            @PathVariable("alarmId") Long alarmId,
            @Valid @RequestBody FalsePositiveRequest request) {
        BAccess.current();
        return commands.reportFalsePositive(alarmId, request);
    }

    @GetMapping("/alarms/{alarmId}/effect")
    public AlarmEffectResponse getAlarmEffect(@PathVariable("alarmId") Long alarmId) {
        BAccess.current();
        return reads.getAlarmEffect(new GetAlarmEffectQuery(alarmId));
    }
}

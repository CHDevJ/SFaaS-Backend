package com.sfaasproject.stf.alarm.controller;

import com.sfaasproject.stf.alarm.service.V7AlarmService;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.inventory.service.V7InventoryService;
import com.sfaasproject.stf.operations.config.V7Backend;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.service.*;
import com.sfaasproject.stf.statistics.service.V7StatisticsService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1")
@V7Backend
public class V7AlarmController {
    private final V7AlarmService alarms;
    private final V7InventoryService inventory;
    private final V7StatisticsService statistics;
    private final V7OverviewService overview;

    public V7AlarmController(
            V7AlarmService alarms,
            V7InventoryService inventory,
            V7StatisticsService statistics,
            V7OverviewService overview) {
        this.alarms = alarms;
        this.inventory = inventory;
        this.statistics = statistics;
        this.overview = overview;
    }

    /** ALM-001 발생 중·종료 알람 통합 목록 */
    @GetMapping("/alarms")
    public AlarmPageResponse getActiveAlarms(
            @RequestParam(name = "lineId", required = false) Long lineId,
            @RequestParam(name = "equipmentId", required = false) Long equipmentId,
            @RequestParam(name = "grade", required = false) AlarmGrade grade,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "processStatus", required = false)
                    AlarmProcessStatus processStatus,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return alarms.list(
                lineId,
                equipmentId,
                processStatus,
                grade,
                (page == null ? 0 : page),
                (size == null ? 20 : size));
    }

    /** ALM-003 알람 상세 조회 */
    @GetMapping("/alarms/{alarmId}")
    public AlarmDetailResponse getAlarmDetail(
            @PathVariable("alarmId") Long alarmId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return alarms.detail(
                alarmId,
                inventory.snapshot().lines().stream()
                        .filter(l -> java.util.Objects.equals(l.activeAlarmId(), alarmId))
                        .map(LineStopForecast::assemblyStopExpectedAt)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null));
    }

    /** ALM-006 조치 기록 및 알람 즉시 종료 */
    @PostMapping("/alarms/{alarmId}/actions")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public AlarmActionResponse createAlarmAction(
            @PathVariable("alarmId") Long alarmId,
            @Valid @RequestBody AlarmActionRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return alarms.complete(alarmId, request, principal.userId());
    }

    /** ALM-002 최근 24시간 알람 이력 */
    @GetMapping("/alarms/recent-24-hours")
    public RecentAlarmListResponse getRecentClosedAlarms(
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return alarms.recent(
                null, null, null, (page == null ? 0 : page), (size == null ? 20 : size));
    }
}

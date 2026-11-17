package com.sfaasproject.stf.operations.controller;

import com.sfaasproject.stf.alarm.service.V7AlarmService;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.inventory.service.V7InventoryService;
import com.sfaasproject.stf.operations.config.V7Backend;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.service.*;
import com.sfaasproject.stf.statistics.service.V7StatisticsService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1")
@V7Backend
public class V7HistoryController {
    private final V7AlarmService alarms;
    private final V7InventoryService inventory;
    private final V7StatisticsService statistics;
    private final V7OverviewService overview;

    public V7HistoryController(
            V7AlarmService alarms,
            V7InventoryService inventory,
            V7StatisticsService statistics,
            V7OverviewService overview) {
        this.alarms = alarms;
        this.inventory = inventory;
        this.statistics = statistics;
        this.overview = overview;
    }

    /** HST-001 종료 알람 이력 조회 */
    @GetMapping("/alarm-histories")
    public AlarmHistoryPageResponse getAlarmHistories(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @RequestParam(name = "equipmentId", required = false) Long equipmentId,
            @RequestParam(name = "grade", required = false) AlarmGrade grade,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.histories(
                from,
                to,
                lineId,
                equipmentId,
                grade,
                (page == null ? 0 : page),
                (size == null ? 20 : size));
    }

    /** HST-002 설비 고장 이력 조회 */
    @GetMapping("/equipment/{equipmentId}/fault-histories")
    public FaultHistoryResponse getEquipmentFaultHistories(
            @PathVariable("equipmentId") Long equipmentId,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.faultHistory(equipmentId, from, to);
    }
}

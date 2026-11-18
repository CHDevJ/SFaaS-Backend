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

@RestController
@RequestMapping("/api/v1")
@V7Backend
public class V7OverviewController {
    private final V7AlarmService alarms;
    private final V7InventoryService inventory;
    private final V7StatisticsService statistics;
    private final V7OverviewService overview;

    public V7OverviewController(
            V7AlarmService alarms,
            V7InventoryService inventory,
            V7StatisticsService statistics,
            V7OverviewService overview) {
        this.alarms = alarms;
        this.inventory = inventory;
        this.statistics = statistics;
        this.overview = overview;
    }

    /** OVW-001 공장 설비 배치도 조회 */
    @GetMapping("/overview/equipment-layout")
    public EquipmentLayoutResponse getEquipmentLayout(
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return overview.layout(lineId);
    }

    /** OVW-002 조립 정지·복구·손실 예측 조회 */
    @GetMapping("/overview/assembly-stop-forecast")
    public AssemblyStopForecastResponse getAssemblyStopForecast(
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return inventory.overview();
    }
}

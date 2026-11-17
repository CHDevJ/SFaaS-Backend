package com.sfaasproject.stf.inventory.controller;

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
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@V7Backend
public class V7InventoryController {
    private final V7AlarmService alarms;
    private final V7InventoryService inventory;
    private final V7StatisticsService statistics;
    private final V7OverviewService overview;

    public V7InventoryController(
            V7AlarmService alarms,
            V7InventoryService inventory,
            V7StatisticsService statistics,
            V7OverviewService overview) {
        this.alarms = alarms;
        this.inventory = inventory;
        this.statistics = statistics;
        this.overview = overview;
    }

    /** INV-001 라인별 현재 재고 조회 */
    @GetMapping("/inventory")
    public InventoryResponse getInventory(
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return inventory.inventory();
    }

    /** INV-002 재고 추이 조회 */
    @GetMapping("/inventory/history")
    public InventoryHistoryResponse getInventoryHistory(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineIds", required = false) List<Long> lineIds,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return inventory.history(from, to, lineIds);
    }

    /** INV-003 라인별 재고 상세 조회 */
    @GetMapping("/inventory/lines/{lineId}")
    public InventoryDetailResponse getInventoryDetail(
            @PathVariable("lineId") Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return inventory.detail(lineId);
    }
}

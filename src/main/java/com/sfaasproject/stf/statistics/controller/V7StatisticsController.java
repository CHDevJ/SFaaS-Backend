package com.sfaasproject.stf.statistics.controller;

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
public class V7StatisticsController {
    private final V7AlarmService alarms;
    private final V7InventoryService inventory;
    private final V7StatisticsService statistics;
    private final V7OverviewService overview;

    public V7StatisticsController(
            V7AlarmService alarms,
            V7InventoryService inventory,
            V7StatisticsService statistics,
            V7OverviewService overview) {
        this.alarms = alarms;
        this.inventory = inventory;
        this.statistics = statistics;
        this.overview = overview;
    }

    /** STA-001 알람 대응 속도 집계 */
    @GetMapping("/statistics/alarm-response")
    public AlarmResponseStatisticsResponse getAlarmResponseStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.response(from, to, lineId);
    }

    /** STA-003 일별 알람 건수 */
    @GetMapping("/statistics/daily-alarms")
    public DailyAlarmStatisticsResponse getDailyAlarmStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.daily(from, to, lineId);
    }

    /** STA-004 설비·라인 가동률 집계 */
    @GetMapping("/statistics/availability")
    public AvailabilityStatisticsResponse getAvailabilityStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.availability(from, to, lineId);
    }

    /** STA-005 설비 정지 시간 집계 */
    @GetMapping("/statistics/downtime")
    public DowntimeStatisticsResponse getDowntimeStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.downtime(from, to, lineId);
    }

    /** STA-006 문제 설비 순위 조회 */
    @GetMapping("/statistics/equipment-rankings")
    public EquipmentRankingResponse getEquipmentRankings(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @RequestParam(name = "sortBy", required = true) String sortBy,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.rankings(from, to, lineId, sortBy);
    }

    /** STA-007 실제 손실액 집계 */
    @GetMapping("/statistics/losses")
    public LossStatisticsResponse getLossStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.losses(from, to, lineId);
    }

    /** STA-009 경보 상위 설비 집계 */
    @GetMapping("/statistics/critical-equipment")
    public CriticalEquipmentResponse sta009Operation(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.critical(from, to, lineId);
    }

    /** STA-010 정지 요인 집계 */
    @GetMapping("/statistics/stop-causes")
    public StopCauseResponse sta010Operation(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return statistics.stopCauses(from, to, lineId);
    }
}

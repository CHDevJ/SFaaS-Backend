package com.sfaasproject.stf.statistics.controller;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.operations.port.BReadPort.*;
import com.sfaasproject.stf.operations.service.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class StatisticsController {
    private final BReadService reads;

    public StatisticsController(BReadService reads) {
        this.reads = reads;
    }

    @GetMapping("/statistics/alarm-response")
    public AlarmResponseStatisticsResponse getAlarmResponseStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getAlarmResponseStatistics(
                new GetAlarmResponseStatisticsQuery(from, to, lineId));
    }

    @GetMapping("/statistics/false-positives")
    public FalsePositiveStatisticsResponse getFalsePositiveStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getFalsePositiveStatistics(
                new GetFalsePositiveStatisticsQuery(from, to, lineId));
    }

    @GetMapping("/statistics/daily-alarms")
    public DailyAlarmStatisticsResponse getDailyAlarmStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getDailyAlarmStatistics(new GetDailyAlarmStatisticsQuery(from, to, lineId));
    }

    @GetMapping("/statistics/availability")
    public AvailabilityStatisticsResponse getAvailabilityStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getAvailabilityStatistics(
                new GetAvailabilityStatisticsQuery(from, to, lineId));
    }

    @GetMapping("/statistics/downtime")
    public DowntimeStatisticsResponse getDowntimeStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getDowntimeStatistics(new GetDowntimeStatisticsQuery(from, to, lineId));
    }

    @GetMapping("/statistics/equipment-rankings")
    public EquipmentRankingResponse getEquipmentRankings(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @RequestParam(name = "sortBy", required = true) GetEquipmentRankingsSortBy sortBy,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getEquipmentRankings(new GetEquipmentRankingsQuery(from, to, lineId, sortBy));
    }

    @GetMapping("/statistics/losses")
    public LossStatisticsResponse getLossStatistics(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getLossStatistics(new GetLossStatisticsQuery(from, to, lineId));
    }

    @GetMapping("/statistics/loss-avoidance")
    public LossAvoidanceResponse getLossAvoidanceStatistics(
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getLossAvoidanceStatistics(new GetLossAvoidanceStatisticsQuery());
    }
}

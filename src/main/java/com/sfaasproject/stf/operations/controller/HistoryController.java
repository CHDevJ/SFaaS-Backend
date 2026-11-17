package com.sfaasproject.stf.operations.controller;

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
public class HistoryController {
    private final BReadService reads;

    public HistoryController(BReadService reads) {
        this.reads = reads;
    }

    @GetMapping("/alarm-histories")
    public AlarmHistoryPageResponse getAlarmHistories(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineId", required = false) Long lineId,
            @RequestParam(name = "equipmentId", required = false) Long equipmentId,
            @RequestParam(name = "grade", required = false) AlarmGrade grade,
            @RequestParam(name = "result", required = false) AlarmResult result,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(name = "sort", required = false) String sort,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getAlarmHistories(
                new GetAlarmHistoriesQuery(
                        from, to, lineId, equipmentId, grade, result, page, size, sort));
    }

    @GetMapping("/equipment/{equipmentId}/fault-histories")
    public FaultHistoryResponse getEquipmentFaultHistories(
            @PathVariable("equipmentId") Long equipmentId,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getEquipmentFaultHistories(
                new GetEquipmentFaultHistoriesQuery(equipmentId, from, to));
    }
}

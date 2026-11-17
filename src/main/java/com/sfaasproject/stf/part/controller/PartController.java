package com.sfaasproject.stf.part.controller;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.operations.port.BReadPort.*;
import com.sfaasproject.stf.operations.service.*;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "stf.backend-b.legacy-v5-enabled", havingValue = "true")
public class PartController {
    private final BReadService reads;
    private final PartApplicationService commands;

    public PartController(BReadService reads, PartApplicationService commands) {
        this.reads = reads;
        this.commands = commands;
    }

    @GetMapping("/equipment/{equipmentId}/parts")
    public InstalledPartListResponse getEquipmentParts(
            @PathVariable("equipmentId") Long equipmentId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getEquipmentParts(new GetEquipmentPartsQuery(equipmentId));
    }

    @GetMapping("/parts/usage")
    public PartUsagePageResponse getPartUsage(
            @RequestParam(name = "lineId", required = false) Long lineId,
            @RequestParam(name = "processId", required = false) Long processId,
            @RequestParam(name = "equipmentId", required = false) Long equipmentId,
            @RequestParam(name = "status", required = false) PartUsageStatus status,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getPartUsage(
                new GetPartUsageQuery(lineId, processId, equipmentId, status, page, size));
    }

    @GetMapping("/equipment/{equipmentId}/parts/{partId}/replacements")
    public PartReplacementHistoryResponse getPartReplacementHistories(
            @PathVariable("equipmentId") Long equipmentId,
            @PathVariable("partId") Long partId,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return reads.getPartReplacementHistories(
                new GetPartReplacementHistoriesQuery(equipmentId, partId, from, to));
    }

    @PostMapping("/equipment/{equipmentId}/parts/{partId}/replacements")
    @ResponseStatus(HttpStatus.CREATED)
    public PartReplacementResponse createPartReplacement(
            @PathVariable("equipmentId") Long equipmentId,
            @PathVariable("partId") Long partId,
            @Valid @RequestBody PartReplacementRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return commands.createPartReplacement(equipmentId, partId, request);
    }
}

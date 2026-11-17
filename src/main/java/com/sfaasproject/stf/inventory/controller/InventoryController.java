package com.sfaasproject.stf.inventory.controller;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BReadPort.*;
import com.sfaasproject.stf.operations.service.*;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class InventoryController {
    private final BReadService reads;

    public InventoryController(BReadService reads) {
        this.reads = reads;
    }

    @GetMapping("/inventory")
    public InventoryResponse getInventory() {
        BAccess.current();
        return reads.getInventory(new GetInventoryQuery());
    }

    @GetMapping("/inventory/history")
    public InventoryHistoryResponse getInventoryHistory(
            @RequestParam(name = "from", required = true) OffsetDateTime from,
            @RequestParam(name = "to", required = true) OffsetDateTime to,
            @RequestParam(name = "lineIds", required = false) List<Long> lineIds) {
        BAccess.current();
        return reads.getInventoryHistory(new GetInventoryHistoryQuery(from, to, lineIds));
    }

    @GetMapping("/inventory/lines/{lineId}")
    public InventoryDetailResponse getInventoryDetail(@PathVariable("lineId") Long lineId) {
        BAccess.current();
        return reads.getInventoryDetail(new GetInventoryDetailQuery(lineId));
    }
}

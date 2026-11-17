package com.sfaasproject.stf.operations.controller;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BReadPort.*;
import com.sfaasproject.stf.operations.service.*;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class OverviewController {
    private final BReadService reads;

    public OverviewController(BReadService reads) {
        this.reads = reads;
    }

    @GetMapping("/overview/equipment-layout")
    public EquipmentLayoutResponse getEquipmentLayout(
            @RequestParam(name = "lineId", required = false) Long lineId) {
        BAccess.current();
        return reads.getEquipmentLayout(new GetEquipmentLayoutQuery(lineId));
    }

    @GetMapping("/overview/assembly-stop-forecast")
    public AssemblyStopForecastResponse getAssemblyStopForecast() {
        BAccess.current();
        return reads.getAssemblyStopForecast(new GetAssemblyStopForecastQuery());
    }
}

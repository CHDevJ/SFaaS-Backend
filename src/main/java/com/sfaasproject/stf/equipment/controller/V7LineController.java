package com.sfaasproject.stf.equipment.controller;

import com.sfaasproject.stf.equipment.service.V7LineQueryService;
import com.sfaasproject.stf.operations.config.V7Backend;
import com.sfaasproject.stf.operations.dto.V7Contract.LineListResponse;

import org.springframework.web.bind.annotation.*;

@RestController
@V7Backend
public class V7LineController {
    private final V7LineQueryService service;

    public V7LineController(V7LineQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/lines")
    public LineListResponse lines() {
        return service.lines();
    }
}

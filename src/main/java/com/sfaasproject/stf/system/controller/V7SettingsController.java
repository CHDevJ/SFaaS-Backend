package com.sfaasproject.stf.system.controller;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;
import com.sfaasproject.stf.operations.config.V7Backend;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.system.service.V7SettingsService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system/settings")
@V7Backend
public class V7SettingsController {
    private final V7SettingsService service;

    public V7SettingsController(V7SettingsService service) {
        this.service = service;
    }

    @GetMapping("/judgement")
    public JudgementSettingsResponse judgement() {
        return service.judgement();
    }

    @GetMapping("/calculation")
    public CalculationSettingsResponse calculation() {
        return service.calculation();
    }

    @PutMapping("/judgement")
    public JudgementSettingsResponse update(
            @Valid @RequestBody JudgementSettings s, @AuthenticationPrincipal Claims actor) {
        return service.update(s, actor.userId());
    }

    @PutMapping("/calculation")
    public CalculationSettingsResponse update(
            @Valid @RequestBody CalculationSettingsUpdateRequest s,
            @AuthenticationPrincipal Claims actor) {
        return service.update(s, actor.userId());
    }
}

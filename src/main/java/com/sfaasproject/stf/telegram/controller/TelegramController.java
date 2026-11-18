package com.sfaasproject.stf.telegram.controller;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BReadPort.*;
import com.sfaasproject.stf.operations.service.*;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.*;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class TelegramController {
    private final TelegramApplicationService commands;

    public TelegramController(TelegramApplicationService commands) {
        this.commands = commands;
    }

    @PostMapping("/telegram/link-tokens")
    public TelegramLinkResponse createMyTelegramLinkToken(
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return commands.createMyTelegramLinkToken();
    }

    @GetMapping("/telegram/me/connection")
    public TelegramConnectionResponse getMyTelegramConnection(
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return commands.getMyTelegramConnection();
    }

    @DeleteMapping("/telegram/me/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkMyTelegram(@AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        commands.unlinkMyTelegram();
    }

    @PostMapping("/telegram/me/test-messages")
    public TelegramTestMessageResponse sendMyTelegramTestMessage(
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.current(principal);
        return commands.sendMyTelegramTestMessage();
    }

    @PostMapping("/system/telegram/test-messages")
    public TelegramTestMessageResponse sendTelegramTestMessage(
            @Valid @RequestBody TelegramTestMessageRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.admin(principal);
        return commands.sendTelegramTestMessage(request);
    }

    @GetMapping("/system/telegram/deliveries")
    public TelegramDeliveryPageResponse getTelegramDeliveries(
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(name = "alarmId", required = false) Long alarmId,
            @RequestParam(name = "result", required = false) DeliveryResult result,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size,
            @AuthenticationPrincipal JwtAccessTokenService.Claims principal) {
        BAccess.admin(principal);
        return commands.getTelegramDeliveries(date, result, page, size, alarmId);
    }
}

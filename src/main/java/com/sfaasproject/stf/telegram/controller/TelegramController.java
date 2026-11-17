package com.sfaasproject.stf.telegram.controller;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BReadPort.*;
import com.sfaasproject.stf.operations.service.*;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
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
    public TelegramLinkResponse createMyTelegramLinkToken() {
        BAccess.current();
        return commands.createMyTelegramLinkToken();
    }

    @GetMapping("/telegram/me/connection")
    public TelegramConnectionResponse getMyTelegramConnection() {
        BAccess.current();
        return commands.getMyTelegramConnection();
    }

    @DeleteMapping("/telegram/me/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkMyTelegram() {
        BAccess.current();
        commands.unlinkMyTelegram();
    }

    @PostMapping("/telegram/me/test-messages")
    public TelegramTestMessageResponse sendMyTelegramTestMessage() {
        BAccess.current();
        return commands.sendMyTelegramTestMessage();
    }

    @PostMapping("/system/telegram/test-messages")
    public TelegramTestMessageResponse sendTelegramTestMessage(
            @Valid @RequestBody TelegramTestMessageRequest request) {
        BAccess.admin();
        return commands.sendTelegramTestMessage(request);
    }

    @GetMapping("/system/telegram/deliveries")
    public TelegramDeliveryPageResponse getTelegramDeliveries(
            @RequestParam(name = "date", required = false) LocalDate date,
            @RequestParam(name = "result", required = false) DeliveryResult result,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size) {
        BAccess.admin();
        return commands.getTelegramDeliveries(date, result, page, size);
    }
}

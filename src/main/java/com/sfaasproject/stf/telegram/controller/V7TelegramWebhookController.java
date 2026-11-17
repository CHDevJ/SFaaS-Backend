package com.sfaasproject.stf.telegram.controller;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.operations.config.V7Backend;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.service.TelegramApplicationService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@V7Backend
public class V7TelegramWebhookController {
    private final byte[] secret;
    private final TelegramApplicationService telegram;
    private final EventClaims claims;
    private final UnitOfWork tx;

    public V7TelegramWebhookController(
            @Value("${stf.telegram.webhook-secret:}") String secret,
            TelegramApplicationService telegram,
            EventClaims claims,
            UnitOfWork tx) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.telegram = telegram;
        this.claims = claims;
        this.tx = tx;
    }

    @PostMapping("/internal/v1/telegram/updates")
    public TelegramUpdateResult accept(
            @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false)
                    String supplied,
            @Valid @RequestBody TelegramUpdate update) {
        if (secret.length == 0
                || supplied == null
                || !MessageDigest.isEqual(secret, supplied.getBytes(StandardCharsets.UTF_8)))
            throw new DomainException(
                    HttpStatus.UNAUTHORIZED, "INTERNAL_API_KEY_INVALID", "인증 정보를 확인해 주세요.");
        if (update.update_id() < 0)
            throw new DomainException(
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "업데이트 번호를 확인해 주세요.");
        boolean processed =
                tx.execute(
                        () -> {
                            if (!claims.claim("v7:telegram-update:" + update.update_id()))
                                return false;
                            if (update.message().chat().id() <= 0) return false;
                            return telegram.start(
                                    update.message().chat().id().toString(),
                                    "private",
                                    update.message().text());
                        });
        return new TelegramUpdateResult(true, new TelegramUpdateResultData(processed));
    }
}

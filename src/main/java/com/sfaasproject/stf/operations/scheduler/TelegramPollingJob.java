package com.sfaasproject.stf.operations.scheduler;

import com.sfaasproject.stf.operations.port.BPorts.EventClaims;
import com.sfaasproject.stf.operations.port.BPorts.UnitOfWork;
import com.sfaasproject.stf.operations.service.TelegramApplicationService;

import org.springframework.scheduling.annotation.Scheduled;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class TelegramPollingJob {
    @FunctionalInterface
    public interface Updates {
        String get(long offset) throws Exception;
    }

    private final Updates updates;
    private final TelegramApplicationService service;
    private final EventClaims claims;
    private final UnitOfWork tx;
    private long offset;
    private final JsonMapper json = JsonMapper.builder().build();

    public TelegramPollingJob(
            Updates updates,
            TelegramApplicationService service,
            EventClaims claims,
            UnitOfWork tx) {
        this.updates = updates;
        this.service = service;
        this.claims = claims;
        this.tx = tx;
    }

    public static Updates http(String token) {
        if (token == null || !token.matches("[0-9]+:[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Bot token is required");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        return offset -> {
            var request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            "https://api.telegram.org/bot"
                                                    + token
                                                    + "/getUpdates?timeout=20&offset="
                                                    + offset))
                            .timeout(Duration.ofSeconds(25))
                            .GET()
                            .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IllegalStateException("Telegram polling failed");
            return response.body();
        };
    }

    @Scheduled(fixedDelayString = "${stf.telegram.poll-delay:1000}")
    public synchronized void poll() {
        try {
            var root = json.readTree(updates.get(offset));
            if (!root.path("ok").asBoolean(false)) return;
            for (var update : root.path("result")) {
                long id = update.path("update_id").asLong();
                if (id < offset) continue;
                var message = update.path("message");
                tx.execute(
                        () -> {
                            if (claims.claim("telegram-update:" + id))
                                service.start(
                                        message.path("chat").path("id").asString(),
                                        message.path("chat").path("type").asString(),
                                        message.path("text").asString());
                            return null;
                        });
                offset = id + 1;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("Telegram polling failed; retrying on next cycle");
        }
    }

    public long nextOffset() {
        return offset;
    }
}

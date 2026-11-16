package com.sfaasproject.stf.telegram.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/** Telegram Bot API sendMessage transport; construct only with a configured bot token. */
public class TelegramBotSender implements TelegramDeliveryService.Sender {
    @FunctionalInterface
    interface Transport {
        Response post(URI uri, String formBody) throws Exception;
    }
    record Response(int statusCode, String body) { }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final URI endpoint;
    private final Transport transport;
    private final JsonMapper json = JsonMapper.builder().build();

    public TelegramBotSender(String botToken) {
        this(botToken, TelegramBotSender::sendHttp);
    }

    TelegramBotSender(String botToken, Transport transport) {
        if (botToken == null || !botToken.matches("^[0-9]+:[A-Za-z0-9_-]+$")) {
            throw new IllegalArgumentException("Telegram bot token is missing or malformed");
        }
        this.endpoint = URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage");
        this.transport = Objects.requireNonNull(transport);
    }

    @Override
    public void send(String chatId, String message) throws Exception {
        if (chatId == null || chatId.isBlank() || message == null
                || message.isBlank() || message.length() > 4096) {
            throw new IllegalArgumentException("Telegram recipient or message is invalid");
        }
        String form = "chat_id=" + encode(chatId) + "&text=" + encode(message);
        try {
            Response response = transport.post(endpoint, form);
            if (response == null || response.statusCode() != 200
                    || response.body() == null
                    || !json.readTree(response.body()).path("ok").asBoolean(false)) {
                throw new TelegramSendException();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TelegramSendException();
        } catch (Exception ex) {
            throw new TelegramSendException();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Response sendHttp(URI uri, String formBody) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();
        var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(
                StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    public static final class TelegramSendException extends Exception {
        public TelegramSendException() {
            super("Telegram send failed");
        }
    }
}

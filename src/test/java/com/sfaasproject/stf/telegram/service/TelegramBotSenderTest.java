package com.sfaasproject.stf.telegram.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TelegramBotSenderTest {
    @Test
    void encodesKoreanMessageAndAcceptsSuccessfulBotResponse() throws Exception {
        AtomicReference<String> form = new AtomicReference<>();
        var sender = new TelegramBotSender("12345:abc_DEF", (uri, body) -> {
            assertEquals("https://api.telegram.org/bot12345:abc_DEF/sendMessage",
                    uri.toString());
            form.set(body);
            return new TelegramBotSender.Response(200, "{\"ok\":true,\"result\":{}}");
        });
        sender.send("123 45", "설비 경보 & 확인");
        assertTrue(form.get().contains("chat_id=123+45"));
        assertTrue(form.get().contains("text="));
        assertFalse(form.get().contains("설비"));
        assertFalse(form.get().contains(" & "));
    }

    @Test
    void treatsBotLevelFailureAsDeliveryFailureWithoutLeakingToken() {
        var sender = new TelegramBotSender("12345:secret", (uri, body) ->
                new TelegramBotSender.Response(200,
                        "{\"ok\":false,\"description\":\"token 12345:secret\"}"));
        var error = assertThrows(TelegramBotSender.TelegramSendException.class,
                () -> sender.send("1", "message"));
        assertFalse(error.toString().contains("12345:secret"));
    }

    @Test
    void rejectsInvalidTokenAndOversizedMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> new TelegramBotSender("short"));
        var sender = new TelegramBotSender("123:token", (uri, body) ->
                new TelegramBotSender.Response(200, "{\"ok\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> sender.send("1", "x".repeat(4097)));
    }
}

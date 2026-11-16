package com.sfaasproject.stf.telegram.service;

import java.time.Duration;

/** Called after the originating transaction commits; failure stays in the delivery result. */
public class TelegramDeliveryService {
    @FunctionalInterface public interface Sender { void send(String chatId, String message) throws Exception; }
    @FunctionalInterface public interface Delay { void waitFor(Duration duration) throws InterruptedException; }
    public record DeliveryResult(boolean successful, int retryCount, String failureReason) { }

    private final Sender sender;
    private final Delay delay;

    public TelegramDeliveryService(Sender sender, Delay delay) {
        this.sender = sender;
        this.delay = delay;
    }

    public DeliveryResult sendWithOneRetry(String chatId, String message) {
        if (chatId == null || chatId.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Recipient and message are required");
        }
        try {
            sender.send(chatId, message);
            return new DeliveryResult(true, 0, null);
        } catch (Exception first) {
            try {
                delay.waitFor(Duration.ofSeconds(1));
                sender.send(chatId, message);
                return new DeliveryResult(true, 1, null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new DeliveryResult(false, 0, "발송 재시도가 중단되었습니다.");
            } catch (Exception second) {
                return new DeliveryResult(false, 1, safeReason(second));
            }
        }
    }

    private String safeReason(Exception ex) {
        // Provider exceptions can contain token, chat ID or request URL; never expose them.
        return "텔레그램 발송에 실패했습니다: " + ex.getClass().getSimpleName();
    }
}

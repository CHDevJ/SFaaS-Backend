package com.sfaasproject.stf.user.service;

import com.sfaasproject.stf.auth.config.TelegramProperties;
import com.sfaasproject.stf.telegram.service.TelegramBotSender;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 기존 텔레그램 전송기(TelegramBotSender, 실패 시 1초 뒤 1회 재시도)로 임시 비밀번호를 보낸다.
 * 봇 토큰(TELEGRAM_BOT_TOKEN)이 없으면 보내지 않고 실패로 돌려준다.
 */
@Component
public class TelegramTemporaryPasswordSender implements TemporaryPasswordSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramTemporaryPasswordSender.class);

    private final TelegramDeliveryService delivery;

    public TelegramTemporaryPasswordSender(TelegramProperties telegram) {
        this.delivery = telegram.canSend()
                ? new TelegramDeliveryService(new TelegramBotSender(telegram.botToken()),
                        duration -> Thread.sleep(duration.toMillis()))
                : null;
    }

    @Override
    public boolean send(String chatId, String message) {
        if (delivery == null) {
            log.warn("Temporary password was not sent: app.telegram.bot-token is not configured.");
            return false;
        }
        TelegramDeliveryService.DeliveryResult result = delivery.sendWithOneRetry(chatId, message);
        if (!result.successful()) {
            log.warn("Temporary password delivery failed: {}", result.failureReason());
        }
        return result.successful();
    }
}

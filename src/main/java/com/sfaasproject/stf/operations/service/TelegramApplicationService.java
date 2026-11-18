package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.telegram.service.*;

import org.springframework.http.HttpStatus;

import java.time.*;

public class TelegramApplicationService {
    public static final String TEST_MESSAGE = "설비 통합관제 테스트 메시지. 연결 확인용 테스트 메시지입니다";
    private final Telegram store;
    private final LinkTokens tokenStore;
    private final Users users;
    private final TelegramLinkTokenService tokens;
    private final TelegramDeliveryService delivery;
    private final Clock clock;
    private final String botUsername;

    public TelegramApplicationService(
            Telegram store,
            LinkTokens tokenStore,
            Users users,
            TelegramLinkTokenService tokens,
            TelegramDeliveryService delivery,
            Clock clock,
            String botUsername) {
        if (botUsername == null || !botUsername.isBlank() && !botUsername.matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Invalid bot username");
        this.store = store;
        this.tokenStore = tokenStore;
        this.users = users;
        this.tokens = tokens;
        this.delivery = delivery;
        this.clock = clock;
        this.botUsername = botUsername;
    }

    public TelegramLinkResponse createMyTelegramLinkToken() {
        long id = BAccess.current().userId();
        if (botUsername.isBlank())
            throw new DomainException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "TELEGRAM_NOT_CONNECTED",
                    "봇 사용자 이름 설정이 필요합니다.");
        var issued = tokens.issue(clock.instant());
        tokenStore.save(id, issued, clock.instant());
        String url = "https://t.me/" + botUsername + "?start=" + issued.rawToken();
        return new TelegramLinkResponse(
                true,
                new TelegramLink(
                        url, url, issued.expiresAt().atZone(clock.getZone()).toOffsetDateTime()));
    }

    public TelegramConnectionResponse getMyTelegramConnection() {
        var connection = store.connection(BAccess.current().userId());
        return new TelegramConnectionResponse(
                true,
                new TelegramConnection(
                        connection != null, connection == null ? null : connection.linkedAt()));
    }

    public void unlinkMyTelegram() {
        store.unlink(BAccess.current().userId());
    }

    public TelegramTestMessageResponse sendMyTelegramTestMessage() {
        return test(BAccess.current().userId());
    }

    public TelegramTestMessageResponse sendTelegramTestMessage(TelegramTestMessageRequest request) {
        BAccess.admin();
        return test(request.userId());
    }

    private TelegramTestMessageResponse test(long id) {
        var result = send(id, null, "TEST", TEST_MESSAGE);
        return new TelegramTestMessageResponse(
                true,
                new TelegramTestMessageResult(
                        TEST_MESSAGE,
                        id,
                        result.successful() ? DeliveryResult.SUCCESS : DeliveryResult.FAILURE,
                        result.failureReason(),
                        OffsetDateTime.now(clock)));
    }

    public TelegramDeliveryService.DeliveryResult send(
            long id, Long alarmId, String type, String message) {
        if (users.active(id) == null) throw Checks.missing("USER_NOT_FOUND");
        var connection = store.connection(id);
        if (connection == null)
            throw new DomainException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "TELEGRAM_NOT_CONNECTED", "텔레그램 연결이 필요합니다.");
        var result = delivery.sendWithOneRetry(connection.chatId(), message);
        store.record(
                new Delivery(
                        alarmId,
                        id,
                        type,
                        message,
                        result.successful(),
                        result.failureReason(),
                        result.retryCount(),
                        OffsetDateTime.now(clock)));
        return result;
    }

    public boolean start(String chatId, String chatType, String text) {
        if (!"private".equals(chatType) || chatId == null || text == null) return false;
        String[] parts = text.trim().split("\\s+", 2);
        if (parts.length != 2 || !parts[0].matches("/start(@[A-Za-z0-9_]+)?")) return false;
        Long id = tokenStore.consume(parts[1]);
        if (id == null || !users.connectable(id)) return false;
        store.link(id, chatId, OffsetDateTime.now(clock));
        return true;
    }

    public TelegramDeliveryPageResponse getTelegramDeliveries(
            LocalDate date, DeliveryResult result, Integer page, Integer size) {
        return getTelegramDeliveries(date, result, page, size, null);
    }

    public TelegramDeliveryPageResponse getTelegramDeliveries(
            LocalDate date, DeliveryResult result, Integer page, Integer size, Long alarmId) {
        BAccess.admin();
        if (alarmId != null && alarmId <= 0) throw Checks.invalid("알람 번호는 양수여야 합니다.");
        Checks.range(page, 0, Integer.MAX_VALUE, "page");
        Checks.range(size, 1, 100, "size");
        var day = date == null ? LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))) : date;
        if (alarmId != null)
            return store.deliveriesForAlarm(
                    day, result, page == null ? 0 : page, size == null ? 20 : size, alarmId);
        return store.deliveries(
                date == null ? LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))) : date,
                result,
                page == null ? 0 : page,
                size == null ? 20 : size);
    }
}

package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

import java.time.*;
import java.util.*;

public class JdbcBTelegram implements Telegram {
    private final JdbcBDatabase db;
    private final Users users;

    public JdbcBTelegram(JdbcBDatabase db, Users users) {
        this.db = db;
        this.users = users;
    }

    @Override
    public Connection connection(long id) {
        var r =
                db.one(
                        "SELECT telegram_chat_id,telegram_linked_at FROM account WHERE account_id=?"
                                + " AND deleted_at IS NULL",
                        id);
        return r == null || r.get("telegram_chat_id") == null
                ? null
                : new Connection(
                        string(r, "telegram_chat_id"), dateTime(r.get("telegram_linked_at")));
    }

    @Override
    public void link(long id, String chatId, OffsetDateTime at) {
        db.update(
                "UPDATE account SET telegram_chat_id=?,telegram_linked_at=?,updated_at=? WHERE"
                        + " account_id=? AND deleted_at IS NULL",
                chatId,
                time(at),
                time(at),
                id);
    }

    @Override
    public void unlink(long id) {
        db.update(
                "UPDATE account SET telegram_chat_id=NULL,telegram_linked_at=NULL,updated_at=?"
                        + " WHERE account_id=?",
                time(db.now()),
                id);
    }

    @Override
    public void record(Delivery d) {
        var user = users.active(d.userId());
        db.update(
                "INSERT INTO"
                    + " notification_log(alarm_id,account_id,receiver_name,notify_type,message,send_result,fail_reason,retry_count,sent_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)",
                d.alarmId(),
                d.userId(),
                user.name(),
                d.type(),
                d.message(),
                d.success() ? "SUCCESS" : "FAILURE",
                d.reason(),
                d.retryCount(),
                time(d.at()));
    }

    @Override
    public TelegramDeliveryPageResponse deliveries(
            LocalDate date, DeliveryResult result, int page, int size) {
        return queryDeliveries(date, result, page, size, null);
    }

    @Override
    public TelegramDeliveryPageResponse deliveriesForAlarm(
            LocalDate date, DeliveryResult result, int page, int size, long alarmId) {
        return queryDeliveries(date, result, page, size, alarmId);
    }

    private TelegramDeliveryPageResponse queryDeliveries(
            LocalDate date, DeliveryResult result, int page, int size, Long alarmId) {
        var rows =
                db
                        .rows(
                                "SELECT * FROM notification_log WHERE sent_at>=? AND sent_at<?"
                                    + " ORDER BY sent_at DESC,notification_id DESC",
                                date.atStartOfDay(),
                                date.plusDays(1).atStartOfDay())
                        .stream()
                        .filter(
                                r ->
                                        alarmId == null
                                                || Objects.equals(
                                                        alarmId,
                                                        r.get("alarm_id") == null
                                                                ? null
                                                                : number(r, "alarm_id")))
                        .toList();
        int success =
                (int) rows.stream().filter(r -> "SUCCESS".equals(string(r, "send_result"))).count();
        var selected =
                rows.stream()
                        .filter(
                                r ->
                                        result == null
                                                || result.name().equals(string(r, "send_result")))
                        .toList();
        var items =
                selected.stream()
                        .skip((long) page * size)
                        .limit(size)
                        .map(
                                r ->
                                        new TelegramDelivery(
                                                number(r, "notification_id"),
                                                dateTime(r.get("sent_at")),
                                                users.historical(number(r, "account_id")) == null
                                                        ? null
                                                        : new UserBrief(
                                                                number(r, "account_id"),
                                                                string(r, "receiver_name"),
                                                                users.historical(
                                                                                number(
                                                                                        r,
                                                                                        "account_id"))
                                                                        .position()),
                                                r.get("alarm_id") == null
                                                        ? null
                                                        : number(r, "alarm_id"),
                                                string(r, "message"),
                                                DeliveryResult.valueOf(string(r, "send_result")),
                                                string(r, "fail_reason"),
                                                string(r, "receiver_name")))
                        .toList();
        var counts = new ArrayList<TelegramRecipientCount>();
        var groups =
                rows.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        r -> number(r, "account_id")));
        groups.forEach(
                (id, list) -> {
                    var user = users.historical(id);
                    if (user != null) {
                        int ok =
                                (int)
                                        list.stream()
                                                .filter(
                                                        r ->
                                                                "SUCCESS"
                                                                        .equals(
                                                                                string(
                                                                                        r,
                                                                                        "send_result")))
                                                .count();
                        counts.add(
                                new TelegramRecipientCount(
                                        user, list.size(), ok, list.size() - ok));
                    }
                });
        return new TelegramDeliveryPageResponse(
                true,
                new TelegramDeliveryPageData(
                        new TelegramDeliverySummary(rows.size(), success, rows.size() - success),
                        items,
                        counts),
                new PageMeta(
                        page, size, (long) selected.size(), (selected.size() + size - 1) / size));
    }
}

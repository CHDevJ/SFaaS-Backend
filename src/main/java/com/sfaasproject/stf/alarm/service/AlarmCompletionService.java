package com.sfaasproject.stf.alarm.service;

import com.sfaasproject.stf.alarm.service.AlarmLifecycleService.*;
import com.sfaasproject.stf.common.DomainException;

import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Supplier;

/** ALM-006 application boundary. The store and event outbox must share the transaction. */
public class AlarmCompletionService {
    public interface Store {
        Alarm lock(long alarmId);

        /** Save action, actor, CLOSED, closedAt and version atomically (compare oldVersion). */
        boolean save(Alarm closed, long oldVersion);
    }

    public interface Transactions {
        <T> T execute(Supplier<T> work);
    }

    public interface Events {
        /** Enqueue in the same transaction; dispatch only after commit. */
        void closed(Alarm closed);
    }

    private final Store store;
    private final Transactions transactions;
    private final Events events;
    private final AlarmLifecycleService lifecycle;
    private final Clock clock;

    public AlarmCompletionService(
            Store store,
            Transactions transactions,
            Events events,
            AlarmLifecycleService lifecycle,
            Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.transactions = Objects.requireNonNull(transactions);
        this.events = Objects.requireNonNull(events);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.clock = Objects.requireNonNull(clock);
    }

    public Action complete(long alarmId, ActionRequest request, Actor authenticatedActor) {
        if (alarmId <= 0)
            throw new DomainException(
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "알람 ID를 확인해 주세요.");
        Objects.requireNonNull(authenticatedActor);
        return transactions.execute(
                () -> {
                    Alarm before = store.lock(alarmId);
                    if (before == null)
                        throw new DomainException(
                                HttpStatus.NOT_FOUND, "ALARM_NOT_FOUND", "알람을 찾을 수 없습니다.");
                    Alarm closed =
                            lifecycle.complete(
                                    before, request, authenticatedActor, OffsetDateTime.now(clock));
                    if (!store.save(closed, before.version()))
                        throw new DomainException(
                                HttpStatus.CONFLICT,
                                "ALARM_STATE_CONFLICT",
                                "알람이 변경되었습니다. 다시 조회해 주세요.");
                    events.closed(closed);
                    return closed.action();
                });
    }
}

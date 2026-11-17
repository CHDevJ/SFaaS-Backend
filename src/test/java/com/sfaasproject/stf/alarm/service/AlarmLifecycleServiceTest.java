package com.sfaasproject.stf.alarm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.alarm.service.AlarmLifecycleService.*;
import com.sfaasproject.stf.common.DomainException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.*;
import java.util.function.Supplier;

class AlarmLifecycleServiceTest {
    final AlarmLifecycleService rules = new AlarmLifecycleService();
    final OffsetDateTime at = OffsetDateTime.parse("2026-11-17T12:00:00+09:00");
    final Actor actor = new Actor(7, "작업자", Role.USER);

    Alarm active() {
        return new Alarm(
                1,
                2,
                3,
                "온도 경보",
                AlarmGrade.CRITICAL,
                ProcessStatus.ACTIVE,
                ValueStatus.ABNORMAL,
                at,
                null,
                null,
                5);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n"})
    void blankCauseUsesAlarmMessageAndClosesEvenAbnormal(String cause) {
        var closed =
                rules.complete(
                        active(),
                        new ActionRequest("자유 입력 설비", cause, "조치 완료"),
                        actor,
                        at.plusMinutes(4));
        assertEquals(ProcessStatus.CLOSED, closed.processStatus());
        assertEquals(ValueStatus.ABNORMAL, closed.valueStatus());
        assertEquals("온도 경보", closed.action().cause());
        assertEquals(240, closed.action().durationSeconds());
        assertEquals(2, closed.equipmentId());
        assertEquals("자유 입력 설비", closed.action().equipmentText());
        assertEquals(actor, closed.action().handledBy());
    }

    @Test
    void normalRecoveryKeepsActiveAndCompletionDoesNotNeedAcknowledgement() {
        var recovered = rules.recover(active(), at.plusMinutes(1));
        assertEquals(ProcessStatus.ACTIVE, recovered.processStatus());
        assertEquals(ValueStatus.RECOVERED, recovered.valueStatus());
        assertSame(recovered, rules.recover(recovered, at.plusMinutes(2)));
        var closed =
                rules.complete(
                        recovered, new ActionRequest("설비", "원인", "내용"), actor, at.plusMinutes(3));
        assertEquals(ProcessStatus.CLOSED, closed.processStatus());
        assertSame(closed, rules.recover(closed, at.plusMinutes(4)));
    }

    @Test
    void duplicateCompletionIs409() {
        var request = new ActionRequest("설비", null, "내용");
        var closed = rules.complete(active(), request, actor, at);
        var error =
                assertThrows(
                        DomainException.class, () -> rules.complete(closed, request, actor, at));
        assertEquals(409, error.status().value());
        assertEquals("ALARM_STATE_CONFLICT", error.code());
    }

    @Test
    void validatesBlankAndContractLengths() {
        for (var request :
                java.util.List.of(
                        new ActionRequest(" ", null, "ok"),
                        new ActionRequest("ok", null, "\n"),
                        new ActionRequest("x".repeat(201), null, "ok"),
                        new ActionRequest("ok", "x".repeat(1001), "ok"),
                        new ActionRequest("ok", null, "x".repeat(4001))))
            assertEquals(
                    "VALIDATION_ERROR",
                    assertThrows(
                                    DomainException.class,
                                    () -> rules.complete(active(), request, actor, at))
                            .code());
        assertEquals(
                4000,
                rules.complete(
                                active(),
                                new ActionRequest(
                                        "x".repeat(200), "x".repeat(1000), "x".repeat(4000)),
                                actor,
                                at)
                        .action()
                        .actionContent()
                        .length());
    }

    @Test
    void staleVersionDoesNotPublishOrReturnSuccess() {
        var store = mock(AlarmCompletionService.Store.class);
        var events = mock(AlarmCompletionService.Events.class);
        when(store.lock(1)).thenReturn(active());
        when(store.save(any(), eq(5L))).thenReturn(false);
        var service = service(store, events);
        assertEquals(
                409,
                assertThrows(
                                DomainException.class,
                                () ->
                                        service.complete(
                                                1, new ActionRequest("설비", null, "조치"), actor))
                        .status()
                        .value());
        verifyNoInteractions(events);
    }

    @Test
    void atomicallySavesBeforeEnqueuingClosureAndMissingAlarmIs404() {
        var store = mock(AlarmCompletionService.Store.class);
        var events = mock(AlarmCompletionService.Events.class);
        when(store.lock(1)).thenReturn(active());
        when(store.save(any(), eq(5L))).thenReturn(true);
        var result = service(store, events).complete(1, new ActionRequest("설비", null, "조치"), actor);
        assertEquals(7, result.handledBy().userId());
        var order = inOrder(store, events);
        order.verify(store).lock(1);
        order.verify(store).save(any(), eq(5L));
        order.verify(events).closed(any());
        assertEquals(
                404,
                assertThrows(
                                DomainException.class,
                                () ->
                                        service(store, events)
                                                .complete(
                                                        9,
                                                        new ActionRequest("설비", null, "조치"),
                                                        actor))
                        .status()
                        .value());
    }

    AlarmCompletionService service(
            AlarmCompletionService.Store store, AlarmCompletionService.Events events) {
        return new AlarmCompletionService(
                store,
                new AlarmCompletionService.Transactions() {
                    public <T> T execute(Supplier<T> s) {
                        return s.get();
                    }
                },
                events,
                rules,
                Clock.fixed(at.plusMinutes(2).toInstant(), ZoneId.of("Asia/Seoul")));
    }
}

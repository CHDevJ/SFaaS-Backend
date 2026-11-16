package com.sfaasproject.stf.measurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sfaasproject.stf.alarm.service.AlarmDecisionService;
import com.sfaasproject.stf.alarm.service.AlarmEvaluationResult;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService;
import com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest;
import com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest.Value;
import com.sfaasproject.stf.measurement.repository.IngestionStore;
import com.sfaasproject.stf.measurement.repository.IngestionStore.DataItem;
import com.sfaasproject.stf.measurement.repository.IngestionStore.Equipment;
import com.sfaasproject.stf.measurement.repository.IngestionStore.Latest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IngestionProcessorTest {
    private final IngestionStore store = mock(IngestionStore.class);
    private final IngestionMessageGuard guard = mock(IngestionMessageGuard.class);
    private final IngestionProcessor processor = new IngestionProcessor(store, guard,
            new IngestionValidationService(), new ProductionCounterService(),
            new AlarmDecisionService(), new EquipmentStatusDecisionService());
    private final OffsetDateTime received = OffsetDateTime.parse("2026-11-16T09:00:03+09:00");
    private final DataItem temperature = new DataItem(7, 1, "temperature", "온도",
            "MEASUREMENT", "NONE", "ABOVE", new BigDecimal("10"),
            new BigDecimal("20"), null, null, null);

    @BeforeEach
    void registeredEquipment() {
        when(store.findActiveEquipment("OR-04-1")).thenReturn(new Equipment(1, "OR-04-1"));
        when(guard.acquire(any())).thenReturn(IngestionMessageGuard.Acquisition.ACQUIRED);
    }

    @Test
    void thirdOutOfRangeBatchEmitsOneAlarmOccurrence() {
        when(store.findActiveItems(1)).thenReturn(List.of(temperature));
        when(store.findLatestForUpdate(7)).thenReturn(null,
                new Latest(new BigDecimal("10"), at(0), 1, 0, 0),
                new Latest(new BigDecimal("11"), at(1), 2, 0, 0));
        for (int minute = 0; minute < 3; minute++) {
            processor.process(request("message-" + minute, minute,
                    new Value("temperature", Value.Kind.MEASUREMENT,
                            BigDecimal.valueOf(10 + minute))), received.plusMinutes(minute));
        }
        var transition = ArgumentCaptor.forClass(AlarmEvaluationResult.class);
        verify(store).applyAlarm(eq(new Equipment(1, "OR-04-1")), eq(temperature),
                eq(new BigDecimal("12")), eq(at(2)), eq(null), transition.capture());
        assertEquals(AlarmEvaluationResult.Transition.OCCUR, transition.getValue().transition());
        verify(store, atLeastOnce()).saveEquipmentStatus(eq(1L),
                eq(EquipmentStatusDecisionService.Status.IDLE), any(), any());
    }

    @Test
    void invalidSecondValueRejectsWholeBatchBeforeAnyWrite() {
        when(store.findActiveItems(1)).thenReturn(List.of(temperature));
        assertThrows(DomainException.class, () -> processor.process(
                request("bad", 0,
                        new Value("temperature", Value.Kind.MEASUREMENT, BigDecimal.TEN),
                        new Value("missing", Value.Kind.STATE, BigDecimal.ONE)), received));
        verify(store, never()).insertMeasurement(anyLong(), any(), any(), any());
        verify(guard).release("bad");
    }

    @Test
    void duplicateMessageSkipsStoreAndReturnsDuplicateResult() {
        when(guard.acquire("duplicate")).thenReturn(IngestionMessageGuard.Acquisition.COMPLETED);
        var accepted = processor.process(request("duplicate", 0,
                new Value("temperature", Value.Kind.MEASUREMENT, BigDecimal.TEN)), received);
        assertEquals("duplicate", accepted.messageId());
        assertEquals(true, accepted.duplicate());
        verify(store, never()).findActiveEquipment(any());
    }

    @Test
    void inProgressMessageIsNotAcknowledgedAsCompletedDuplicate() {
        when(guard.acquire("processing")).thenReturn(IngestionMessageGuard.Acquisition.IN_PROGRESS);
        assertThrows(DomainException.class, () -> processor.process(request("processing", 0,
                new Value("temperature", Value.Kind.MEASUREMENT, BigDecimal.TEN)), received));
        verify(store, never()).insertMeasurement(anyLong(), any(), any(), any());
    }

    @Test
    void cumulativeProductionAddsOnlyDifferenceFromPreviousCounter() {
        var counter = new DataItem(8, 1, "prod_count", "생산 수", "CUMULATIVE",
                "PRODUCTION", null, null, null, null, null, LocalTime.MIDNIGHT);
        when(store.findActiveItems(1)).thenReturn(List.of(counter));
        when(store.findLatestForUpdate(8)).thenReturn(new Latest(new BigDecimal("100"),
                at(0), 0, 0, 0));
        processor.process(request("counter", 1,
                new Value("prod_count", Value.Kind.CUMULATIVE, new BigDecimal("103"))),
                received.plusMinutes(1));
        verify(store).addProduction(1, at(1), 3);
    }

    private MeasurementIngestionRequest request(String messageId, int minute, Value... values) {
        return new MeasurementIngestionRequest(messageId, "OR-04-1",
                OffsetDateTime.parse("2026-11-16T09:00:00+09:00").plusMinutes(minute),
                List.of(values));
    }

    private LocalDateTime at(int minute) {
        return LocalDateTime.of(2026, 11, 16, 9, minute);
    }
}

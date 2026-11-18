package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.operations.port.BPorts.UnitOfWork;
import com.sfaasproject.stf.statistics.port.AssemblyStopObservationQuery;
import com.sfaasproject.stf.statistics.port.AssemblyStopObservationQuery.*;
import com.sfaasproject.stf.statistics.repository.AssemblyStopRepository;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

class AssemblyStopRecorderTest {
    final AssemblyStopObservationQuery query = mock(AssemblyStopObservationQuery.class);
    final AssemblyStopRepository repository = mock(AssemblyStopRepository.class);
    final UnitOfWork tx =
            new UnitOfWork() {
                public <T> T execute(Supplier<T> task) {
                    return task.get();
                }
            };
    final AssemblyStopRecorder recorder =
            new AssemblyStopRecorder(query, repository, new ActualLossCalculator(), tx);
    final OffsetDateTime at = OffsetDateTime.parse("2026-11-18T10:00:00+09:00");
    final Cause cause = new Cause(1L, 2L, 3L, at);

    @BeforeEach
    void ready() {
        when(repository.open()).thenReturn(List.of());
    }

    @Test
    void serializesBeforeReadingCrossDomainEvidence() {
        when(query.observe()).thenReturn(new Observation(at, List.of(cause), false, 360, 0L));
        recorder.refresh();
        var order = inOrder(repository, query);
        order.verify(repository).lock();
        order.verify(query).observe();
        order.verify(repository).open();
        order.verify(repository).start(cause, at, 360, 0);
    }

    @Test
    void missingSettingsDoNotCreateInventedSnapshot() {
        for (Integer output : new Integer[] {null, 0}) {
            when(query.observe())
                    .thenReturn(new Observation(at, List.of(cause), false, output, 50000L));
            recorder.refresh();
        }
        verify(repository, never()).start(any(), any(), anyInt(), anyLong());
    }

    @Test
    void uncertainRecoveryKeepsTheExistingInterval() {
        var stop = new AssemblyStopRepository.OpenStop(1, at.minusMinutes(1), 360, 50000);
        when(repository.open()).thenReturn(List.of(stop));
        when(query.observe()).thenReturn(new Observation(at, List.of(), false, 360, 50000L));
        recorder.refresh();
        verify(repository, never()).end(any(), any(), any());
    }

    @Test
    void recoveryUsesStartSnapshotEvenWhenCurrentSettingsChange() {
        var stop = new AssemblyStopRepository.OpenStop(1, at.minusMinutes(20), 360, 50000);
        when(repository.open()).thenReturn(List.of(stop));
        when(query.observe()).thenReturn(new Observation(at, List.of(), true, 1, 1L));
        recorder.refresh();
        verify(repository).end(stop, at, new BigDecimal("6000000"));
    }

    @Test
    void currentCauseDoesNotDuplicateExistingStop() {
        when(repository.open())
                .thenReturn(List.of(new AssemblyStopRepository.OpenStop(1, at, 360, 50000)));
        when(query.observe()).thenReturn(new Observation(at, List.of(cause), false, 360, 50000L));
        recorder.refresh();
        verify(repository, never()).start(any(), any(), anyInt(), anyLong());
    }
}

package com.sfaasproject.stf.alarm.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowDecisionService.Event;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.EffectStatus;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.FalsePositiveReason;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.ProcessStatus;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.Result;
import com.sfaasproject.stf.common.DomainException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmWorkflowDecisionServiceTest {
    private final AlarmWorkflowDecisionService service = new AlarmWorkflowDecisionService();
    private final LocalDateTime t = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Test
    void acknowledgementRecordsDistinctActorAndMaintenanceAssigneeOnce() {
        var decision = service.acknowledge(AlarmWorkflowState.initial(), 10, 20,
                true, "USER", t);
        assertEquals(ProcessStatus.ACKNOWLEDGED, decision.state().processStatus());
        assertEquals(10L, decision.state().acknowledgedBy());
        assertEquals(20L, decision.state().assigneeId());
        assertEquals(List.of(Event.CONFIRM), decision.events());
        assertEquals("ALARM_ALREADY_ACKNOWLEDGED", assertThrows(DomainException.class,
                () -> service.acknowledge(decision.state(), 10, 20, true, "USER", t)).code());
    }

    @Test
    void onlyActiveMaintenanceAccountCanBeAssigned() {
        assertEquals("ASSIGNEE_NOT_USER", assertThrows(DomainException.class,
                () -> service.acknowledge(AlarmWorkflowState.initial(), 10, 20,
                        true, "ADMIN", t)).code());
        assertThrows(DomainException.class, () -> service.acknowledge(
                AlarmWorkflowState.initial(), 10, 20, false, "USER", t));
    }

    @Test
    void actionRequiresContentAndAcknowledgement() {
        assertEquals("ALARM_STATE_CONFLICT", assertThrows(DomainException.class,
                () -> service.recordAction(AlarmWorkflowState.initial(), "원인", "조치", t, 20)).code());
        var acknowledged = acknowledged();
        assertEquals("ACTION_CONTENT_REQUIRED", assertThrows(DomainException.class,
                () -> service.recordAction(acknowledged, "원인", " ", t, 20)).code());
        var action = service.recordAction(acknowledged, "원인", "부품 점검", t, 20);
        assertEquals(ProcessStatus.ACTION, action.state().processStatus());
        assertEquals(EffectStatus.WAITING_FOR_RECOVERY, action.state().effectStatus());
        assertEquals(List.of(Event.ACTION), action.events());
    }

    @Test
    void recoveryStartsVerificationButDoesNotCloseWithoutProduction() {
        var action = service.recordAction(acknowledged(), "원인", "조치", t, 20).state();
        var recovered = service.markRecovered(action, t.plusMinutes(1));
        assertEquals(ProcessStatus.ACTION, recovered.state().processStatus());
        assertEquals(EffectStatus.IN_PROGRESS, recovered.state().effectStatus());
        assertNotNull(recovered.state().verificationStartedAt());
        assertEquals(List.of(Event.RECOVER), recovered.events());
        assertEquals(recovered.state(), service.registerInspectorPasses(recovered.state(), 0,
                t.plusMinutes(2)).state());
    }

    @Test
    void closesOnlyWhenTargetProducedAndKeepsTimelineOrder() {
        var action = service.recordAction(acknowledged(), "원인", "조치", t, 20).state();
        var recovered = service.markRecovered(action, t.plusMinutes(1)).state();
        var nineteen = service.registerInspectorPasses(recovered, 19, t.plusMinutes(2));
        assertEquals(ProcessStatus.ACTION, nineteen.state().processStatus());
        assertTrue(nineteen.events().isEmpty());
        var twenty = service.registerInspectorPasses(nineteen.state(), 1, t.plusMinutes(3));
        assertEquals(ProcessStatus.CLOSED, twenty.state().processStatus());
        assertEquals(Result.EFFECTIVE, twenty.state().result());
        assertEquals(List.of(Event.CLOSE), twenty.events());
        assertEquals(20, twenty.state().currentProductionCount());
    }

    @Test
    void recurrenceResetsVerificationAndKeepsSameAlarmOpen() {
        var action = service.recordAction(acknowledged(), "원인", "조치", t, 20).state();
        var recovered = service.markRecovered(action, t.plusMinutes(1)).state();
        var partial = service.registerInspectorPasses(recovered, 8, t.plusMinutes(2)).state();
        var recurrence = service.markRecurred(partial);
        assertEquals(ProcessStatus.ACTION, recurrence.state().processStatus());
        assertEquals(EffectStatus.RECURRED, recurrence.state().effectStatus());
        assertEquals(0, recurrence.state().currentProductionCount());
        assertEquals(1, recurrence.state().recurrenceCount());
        assertFalse(recurrence.state().valueRecovered());
        assertEquals(List.of(Event.RECUR), recurrence.events());
        assertEquals(recurrence.state(), service.registerInspectorPasses(recurrence.state(), 30,
                t.plusMinutes(3)).state());
    }

    @Test
    void falsePositiveRequiresAcknowledgementAndClosesWithTwoOrderedEvents() {
        assertEquals("FALSE_POSITIVE_NOT_ALLOWED", assertThrows(DomainException.class,
                () -> service.reportFalsePositive(AlarmWorkflowState.initial(),
                        FalsePositiveReason.SENSOR_MALFUNCTION, "sensor", t)).code());
        var decision = service.reportFalsePositive(acknowledged(),
                FalsePositiveReason.SENSOR_MALFUNCTION, "sensor", t);
        assertEquals(ProcessStatus.CLOSED, decision.state().processStatus());
        assertEquals(Result.FALSE_POSITIVE, decision.state().result());
        assertEquals(List.of(Event.FALSE_POSITIVE, Event.CLOSE), decision.events());
        assertThrows(DomainException.class, () -> service.reportFalsePositive(decision.state(),
                FalsePositiveReason.OTHER, null, t));
    }

    private AlarmWorkflowState acknowledged() {
        return service.acknowledge(AlarmWorkflowState.initial(), 10, 20,
                true, "USER", t.minusMinutes(1)).state();
    }
}

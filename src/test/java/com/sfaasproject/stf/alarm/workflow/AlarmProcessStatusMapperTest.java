package com.sfaasproject.stf.alarm.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.ProcessStatus;
import org.junit.jupiter.api.Test;

class AlarmProcessStatusMapperTest {
    private final AlarmProcessStatusMapper mapper = new AlarmProcessStatusMapper();

    @Test
    void actionUsesErdsActionTakenWhileApiUsesAction() {
        assertEquals("ACTION_TAKEN", mapper.toDatabase(ProcessStatus.ACTION));
        assertEquals(ProcessStatus.ACTION, mapper.fromDatabase("ACTION_TAKEN"));
        assertEquals(ProcessStatus.UNACKNOWLEDGED,
                mapper.fromDatabase("UNACKNOWLEDGED"));
    }
}

package com.sfaasproject.stf.alarm.workflow;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.ProcessStatus;
import org.springframework.stereotype.Component;

/** The public API uses ACTION while the approved ERD stores ACTION_TAKEN. */
@Component
public class AlarmProcessStatusMapper {
    public String toDatabase(ProcessStatus status) {
        if (status == null) throw new IllegalArgumentException("Status is required");
        return status == ProcessStatus.ACTION ? "ACTION_TAKEN" : status.name();
    }

    public ProcessStatus fromDatabase(String value) {
        if (value == null) throw new IllegalArgumentException("Status is required");
        return "ACTION_TAKEN".equals(value) ? ProcessStatus.ACTION
                : ProcessStatus.valueOf(value);
    }
}

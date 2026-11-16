package com.sfaasproject.stf.alarm.workflow;

import java.time.LocalDateTime;

/** Persistent alarm/process fields needed by the backend-only workflow rules. */
public record AlarmWorkflowState(
        ProcessStatus processStatus,
        boolean valueRecovered,
        Long acknowledgedBy,
        Long assigneeId,
        LocalDateTime acknowledgedAt,
        LocalDateTime actionedAt,
        LocalDateTime closedAt,
        Result result,
        FalsePositiveReason falsePositiveReason,
        String falsePositiveDetail,
        EffectStatus effectStatus,
        int targetProductionCount,
        int currentProductionCount,
        LocalDateTime verificationStartedAt,
        int recurrenceCount) {

    public enum ProcessStatus { UNACKNOWLEDGED, ACKNOWLEDGED, ACTION, CLOSED }
    public enum Result { EFFECTIVE, FALSE_POSITIVE }
    public enum EffectStatus { WAITING_FOR_RECOVERY, IN_PROGRESS, PASSED, RECURRED }
    public enum FalsePositiveReason {
        SENSOR_MALFUNCTION, THRESHOLD_MISMATCH, INSPECTION_OR_TRIAL, OTHER
    }

    public AlarmWorkflowState {
        if (processStatus == null || effectStatus == null || targetProductionCount < 0
                || currentProductionCount < 0 || recurrenceCount < 0) {
            throw new IllegalArgumentException("Invalid alarm workflow state");
        }
    }

    public static AlarmWorkflowState initial() {
        return new AlarmWorkflowState(ProcessStatus.UNACKNOWLEDGED, false,
                null, null, null, null, null, null, null, null,
                EffectStatus.WAITING_FOR_RECOVERY, 0, 0, null, 0);
    }
}

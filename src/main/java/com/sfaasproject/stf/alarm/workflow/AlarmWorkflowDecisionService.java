package com.sfaasproject.stf.alarm.workflow;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.EffectStatus;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.FalsePositiveReason;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.ProcessStatus;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.Result;
import com.sfaasproject.stf.common.DomainException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** SF5-35/36 transitions. The caller writes state, action and logs atomically. */
@Service
public class AlarmWorkflowDecisionService {
    public enum Event { CONFIRM, ACTION, RECOVER, RECUR, FALSE_POSITIVE, CLOSE }
    public record Decision(AlarmWorkflowState state, List<Event> events) { }

    public Decision acknowledge(AlarmWorkflowState before, long actorId, long assigneeId,
            boolean assigneeActive, String assigneeRole, LocalDateTime now) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(now);
        if (before.processStatus() != ProcessStatus.UNACKNOWLEDGED) {
            throw error(HttpStatus.CONFLICT, "ALARM_ALREADY_ACKNOWLEDGED", "이미 확인된 알람입니다.");
        }
        if (actorId <= 0 || assigneeId <= 0 || !assigneeActive
                || !"MAINTENANCE".equals(assigneeRole)) {
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "ASSIGNEE_NOT_MAINTENANCE",
                    "사용 중인 보전 담당자를 선택해 주세요.");
        }
        return new Decision(copy(before, ProcessStatus.ACKNOWLEDGED, before.valueRecovered(),
                actorId, assigneeId, now, before.actionedAt(), before.closedAt(), before.result(),
                before.falsePositiveReason(), before.falsePositiveDetail(), before.effectStatus(),
                before.targetProductionCount(), before.currentProductionCount(),
                before.verificationStartedAt(), before.recurrenceCount()), List.of(Event.CONFIRM));
    }

    public Decision recordAction(AlarmWorkflowState before, String cause, String actionContent,
            LocalDateTime now, int targetProductionCount) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(now);
        if (actionContent == null || actionContent.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "ACTION_CONTENT_REQUIRED", "조치 내용을 입력해 주세요.");
        }
        if (cause == null || cause.isBlank() || cause.length() > 1000
                || actionContent.length() > 4000 || targetProductionCount <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "조치 내용을 확인해 주세요.");
        }
        if (before.processStatus() != ProcessStatus.ACKNOWLEDGED
                && before.processStatus() != ProcessStatus.ACTION) {
            throw error(HttpStatus.CONFLICT, "ALARM_STATE_CONFLICT", "현재 단계에서는 처리할 수 없습니다.");
        }
        // A recovered value can start verification at action time; otherwise wait for recovery.
        EffectStatus effect = before.valueRecovered() ? EffectStatus.IN_PROGRESS
                : EffectStatus.WAITING_FOR_RECOVERY;
        return new Decision(copy(before, ProcessStatus.ACTION, before.valueRecovered(),
                before.acknowledgedBy(), before.assigneeId(), before.acknowledgedAt(), now,
                null, null, null, null, effect, targetProductionCount, 0,
                before.valueRecovered() ? now : null, before.recurrenceCount()), List.of(Event.ACTION));
    }

    public Decision markRecovered(AlarmWorkflowState before, LocalDateTime now) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(now);
        if (before.processStatus() == ProcessStatus.CLOSED || before.valueRecovered()) {
            return new Decision(before, List.of());
        }
        boolean verifying = before.processStatus() == ProcessStatus.ACTION;
        return new Decision(copy(before, before.processStatus(), true,
                before.acknowledgedBy(), before.assigneeId(), before.acknowledgedAt(),
                before.actionedAt(), before.closedAt(), before.result(), before.falsePositiveReason(),
                before.falsePositiveDetail(), verifying ? EffectStatus.IN_PROGRESS : before.effectStatus(),
                before.targetProductionCount(), 0, verifying ? now : null, before.recurrenceCount()),
                List.of(Event.RECOVER));
    }

    public Decision registerInspectorPasses(AlarmWorkflowState before, int quantity, LocalDateTime now) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(now);
        if (quantity < 0) throw new IllegalArgumentException("Production quantity cannot be negative");
        if (quantity == 0 || before.processStatus() != ProcessStatus.ACTION
                || before.effectStatus() != EffectStatus.IN_PROGRESS
                || !before.valueRecovered()) {
            return new Decision(before, List.of());
        }
        int count = Math.addExact(before.currentProductionCount(), quantity);
        boolean passed = count >= before.targetProductionCount();
        return new Decision(copy(before, passed ? ProcessStatus.CLOSED : before.processStatus(),
                before.valueRecovered(), before.acknowledgedBy(), before.assigneeId(),
                before.acknowledgedAt(), before.actionedAt(), passed ? now : before.closedAt(),
                passed ? Result.EFFECTIVE : before.result(), before.falsePositiveReason(),
                before.falsePositiveDetail(), passed ? EffectStatus.PASSED : before.effectStatus(),
                before.targetProductionCount(), count, before.verificationStartedAt(),
                before.recurrenceCount()), passed ? List.of(Event.CLOSE) : List.of());
    }

    public Decision markRecurred(AlarmWorkflowState before) {
        Objects.requireNonNull(before);
        if (before.processStatus() == ProcessStatus.CLOSED || !before.valueRecovered()) {
            return new Decision(before, List.of());
        }
        return new Decision(copy(before, before.processStatus(), false,
                before.acknowledgedBy(), before.assigneeId(), before.acknowledgedAt(),
                before.actionedAt(), before.closedAt(), before.result(), before.falsePositiveReason(),
                before.falsePositiveDetail(), before.processStatus() == ProcessStatus.ACTION
                        ? EffectStatus.RECURRED : before.effectStatus(),
                before.targetProductionCount(), 0, null,
                Math.addExact(before.recurrenceCount(), 1)), List.of(Event.RECUR));
    }

    public Decision reportFalsePositive(AlarmWorkflowState before, FalsePositiveReason reason,
            String detail, LocalDateTime now) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(now);
        if (before.processStatus() == ProcessStatus.UNACKNOWLEDGED) {
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "FALSE_POSITIVE_NOT_ALLOWED",
                    "알람을 먼저 확인해 주세요.");
        }
        if (before.processStatus() == ProcessStatus.CLOSED) {
            throw error(HttpStatus.CONFLICT, "ALARM_STATE_CONFLICT", "이미 종료된 알람입니다.");
        }
        if (reason == null || detail != null && detail.length() > 2000) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "오경보 사유를 확인해 주세요.");
        }
        return new Decision(copy(before, ProcessStatus.CLOSED, before.valueRecovered(),
                before.acknowledgedBy(), before.assigneeId(), before.acknowledgedAt(),
                before.actionedAt(), now, Result.FALSE_POSITIVE, reason, detail,
                before.effectStatus(), before.targetProductionCount(), before.currentProductionCount(),
                before.verificationStartedAt(), before.recurrenceCount()),
                List.of(Event.FALSE_POSITIVE, Event.CLOSE));
    }

    private AlarmWorkflowState copy(AlarmWorkflowState old, ProcessStatus process,
            boolean valueRecovered, Long acknowledgedBy, Long assigneeId,
            LocalDateTime acknowledgedAt, LocalDateTime actionedAt, LocalDateTime closedAt,
            Result result, FalsePositiveReason reason, String detail, EffectStatus effect,
            int target, int count, LocalDateTime verifyStarted, int recurrence) {
        return new AlarmWorkflowState(process, valueRecovered, acknowledgedBy, assigneeId,
                acknowledgedAt, actionedAt, closedAt, result, reason, detail, effect,
                target, count, verifyStarted, recurrence);
    }

    private DomainException error(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}

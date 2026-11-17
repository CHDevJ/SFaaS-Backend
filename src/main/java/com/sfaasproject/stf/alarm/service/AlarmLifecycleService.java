package com.sfaasproject.stf.alarm.service;

import com.sfaasproject.stf.common.DomainException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/** v6 lifecycle; value recovery never completes an operator's action. */
@Service
public class AlarmLifecycleService {
    public enum ProcessStatus {
        ACTIVE,
        CLOSED
    }

    public enum ValueStatus {
        ABNORMAL,
        RECOVERED
    }

    public enum Role {
        ADMIN,
        USER
    }

    public record Actor(long userId, String name, Role permission) {
        public Actor {
            if (userId <= 0 || name == null || name.isBlank() || permission == null)
                throw new IllegalArgumentException("An authenticated actor is required");
        }
    }

    public record ActionRequest(String equipmentText, String cause, String actionContent) {}

    public record Action(
            long alarmId,
            ProcessStatus processStatus,
            String equipmentText,
            String cause,
            String actionContent,
            OffsetDateTime closedAt,
            Actor handledBy,
            long durationSeconds) {}

    public record Alarm(
            long alarmId,
            long equipmentId,
            long dataItemId,
            String message,
            AlarmGrade grade,
            ProcessStatus processStatus,
            ValueStatus valueStatus,
            OffsetDateTime occurredAt,
            OffsetDateTime recoveredAt,
            Action action,
            long version) {
        public Alarm {
            if (alarmId <= 0
                    || equipmentId <= 0
                    || dataItemId <= 0
                    || message == null
                    || message.isBlank()
                    || message.length() > 1000
                    || grade == null
                    || processStatus == null
                    || valueStatus == null
                    || occurredAt == null
                    || version < 0
                    || (processStatus == ProcessStatus.CLOSED) != (action != null))
                throw new IllegalArgumentException("Invalid v6 alarm snapshot");
        }
    }

    public Alarm complete(Alarm before, ActionRequest request, Actor actor, OffsetDateTime now) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(actor);
        if (before.processStatus() == ProcessStatus.CLOSED)
            throw new DomainException(HttpStatus.CONFLICT, "ALARM_STATE_CONFLICT", "이미 종료된 알람입니다.");
        if (request == null
                || blankOrLong(request.equipmentText(), 200)
                || blankOrLong(request.actionContent(), 4000)
                || request.cause() != null && request.cause().length() > 1000)
            throw new DomainException(
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "설비 글과 조치 상세를 확인해 주세요.");
        if (now == null || now.isBefore(before.occurredAt()))
            throw new IllegalArgumentException("Action cannot precede alarm occurrence");
        String cause =
                request.cause() == null || request.cause().isBlank()
                        ? before.message()
                        : request.cause();
        var action =
                new Action(
                        before.alarmId(),
                        ProcessStatus.CLOSED,
                        request.equipmentText(),
                        cause,
                        request.actionContent(),
                        now,
                        actor,
                        Duration.between(before.occurredAt(), now).toSeconds());
        return new Alarm(
                before.alarmId(),
                before.equipmentId(),
                before.dataItemId(),
                before.message(),
                before.grade(),
                ProcessStatus.CLOSED,
                before.valueStatus(),
                before.occurredAt(),
                before.recoveredAt(),
                action,
                Math.addExact(before.version(), 1));
    }

    public Alarm recover(Alarm before, OffsetDateTime now) {
        Objects.requireNonNull(before);
        if (now == null || now.isBefore(before.occurredAt()))
            throw new IllegalArgumentException("Recovery cannot precede alarm occurrence");
        if (before.processStatus() == ProcessStatus.CLOSED
                || before.valueStatus() == ValueStatus.RECOVERED) return before;
        return new Alarm(
                before.alarmId(),
                before.equipmentId(),
                before.dataItemId(),
                before.message(),
                before.grade(),
                ProcessStatus.ACTIVE,
                ValueStatus.RECOVERED,
                before.occurredAt(),
                now,
                null,
                Math.addExact(before.version(), 1));
    }

    private boolean blankOrLong(String text, int max) {
        return text == null || text.isBlank() || text.length() > max;
    }
}

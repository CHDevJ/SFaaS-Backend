package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowDecisionService;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState;
import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;

public class AlarmApplicationService {
    private final Alarms alarms;
    private final Users users;
    private final Settings settings;
    private final UnitOfWork transactions;
    private final BEventPublisher events;
    private final AlarmWorkflowDecisionService rules;
    private final PartApplicationService parts;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public AlarmApplicationService(
            Alarms alarms,
            Users users,
            Settings settings,
            UnitOfWork transactions,
            BEventPublisher events,
            AlarmWorkflowDecisionService rules,
            PartApplicationService parts,
            Clock clock) {
        this.alarms = alarms;
        this.users = users;
        this.settings = settings;
        this.transactions = transactions;
        this.events = events;
        this.rules = rules;
        this.parts = parts;
        this.clock = clock;
    }

    public AlarmAcknowledgementResponse acknowledgeAlarm(
            Long id, AlarmAcknowledgementRequest request) {
        var actor = BAccess.current();
        return transactions.execute(
                () -> {
                    var alarm = load(id);
                    var assignee = users.active(request.assigneeUserId());
                    var by = users.active(actor.userId());
                    if (by == null) throw Checks.missing("USER_NOT_FOUND");
                    var now = now();
                    var decision =
                            rules.acknowledge(
                                    alarm.state(),
                                    actor.userId(),
                                    request.assigneeUserId(),
                                    assignee != null,
                                    assignee == null ? null : assignee.position().name(),
                                    now.toLocalDateTime());
                    save(
                            alarm,
                            decision.state(),
                            new Mutation("CONFIRM", actor.userId(), null, null, now));
                    events.publish(
                            new BEvents.Notification(
                                    BEvents.id(),
                                    sequence.incrementAndGet(),
                                    request.assigneeUserId(),
                                    "ASSIGN",
                                    id,
                                    "알람 조치 담당자로 지정되었습니다.",
                                    now));
                    return new AlarmAcknowledgementResponse(
                            true,
                            new AlarmAcknowledgementData(
                                    id, AlarmProcessStatus.ACKNOWLEDGED, by, now, assignee));
                });
    }

    public AlarmActionResponse createAlarmAction(Long id, AlarmActionRequest request) {
        var actor = BAccess.current();
        return transactions.execute(
                () -> {
                    var alarm = load(id);
                    var now = now();
                    var user = users.active(actor.userId());
                    if (user == null) throw Checks.missing("USER_NOT_FOUND");
                    if (request.replacedPartId() == null
                            && (request.replacedAt() != null
                                    || request.replacementReason() != null))
                        throw Checks.invalid("교체 부품을 선택해 주세요.");
                    var decision =
                            rules.recordAction(
                                    alarm.state(),
                                    request.cause(),
                                    request.actionContent(),
                                    now.toLocalDateTime(),
                                    settings.integer("VERIFY_PASS_COUNT", 20));
                    Long replacementId = null;
                    // Adapter saves action and optional replacement in the same transaction.
                    Long actionId =
                            save(
                                    alarm,
                                    decision.state(),
                                    new Mutation(
                                            "ACTION",
                                            actor.userId(),
                                            request.cause(),
                                            request.actionContent(),
                                            now));
                    if (request.replacedPartId() != null) {
                        Checks.required(request.replacedAt(), "replacedAt");
                        if (request.replacementReason() == null
                                || request.replacementReason().isBlank())
                            throw Checks.invalid("교체 사유를 입력해 주세요.");
                        replacementId =
                                parts.replace(
                                                alarm.equipmentId(),
                                                request.replacedPartId(),
                                                new PartReplacementRequest(
                                                        request.replacedAt(),
                                                        request.replacementReason(),
                                                        request.actionContent()),
                                                actor.userId(),
                                                actionId)
                                        .replacementId();
                    }
                    return new AlarmActionResponse(
                            true,
                            new AlarmActionData(
                                    id, AlarmProcessStatus.ACTION, now, user, replacementId));
                });
    }

    public AlarmClosureResponse reportFalsePositive(Long id, FalsePositiveRequest request) {
        var actor = BAccess.current();
        return transactions.execute(
                () -> {
                    var alarm = load(id);
                    var now = now();
                    var decision =
                            rules.reportFalsePositive(
                                    alarm.state(),
                                    AlarmWorkflowState.FalsePositiveReason.valueOf(
                                            request.reason().name()),
                                    request.detail(),
                                    now.toLocalDateTime());
                    save(
                            alarm,
                            decision.state(),
                            new Mutation(
                                    "FALSE_POSITIVE",
                                    actor.userId(),
                                    request.reason().name(),
                                    request.detail(),
                                    now));
                    return new AlarmClosureResponse(
                            true,
                            new AlarmClosureData(
                                    id,
                                    AlarmProcessStatus.CLOSED,
                                    AlarmResult.FALSE_POSITIVE,
                                    now));
                });
    }

    public void valueChanged(long id, boolean recovered, OffsetDateTime now) {
        transactions.execute(
                () -> {
                    var alarm = load(id);
                    var decision =
                            recovered
                                    ? rules.markRecovered(alarm.state(), now.toLocalDateTime())
                                    : rules.markRecurred(alarm.state());
                    if (!decision.events().isEmpty()) {
                        save(
                                alarm,
                                decision.state(),
                                new Mutation(
                                        recovered ? "RECOVER" : "RECUR", null, null, null, now));
                        if (!recovered && alarm.state().assigneeId() != null)
                            events.publish(
                                    new BEvents.Notification(
                                            BEvents.id(),
                                            sequence.incrementAndGet(),
                                            alarm.state().assigneeId(),
                                            "RECUR",
                                            id,
                                            "담당 알람이 재발했습니다.",
                                            now));
                    }
                    return null;
                });
    }

    public void inspectorPassed(long lineId, int quantity, OffsetDateTime now) {
        transactions.execute(
                () -> {
                    for (long id : alarms.verifyingInLine(lineId)) {
                        var alarm = load(id);
                        var next =
                                rules.registerInspectorPasses(
                                        alarm.state(), quantity, now.toLocalDateTime());
                        if (next.state() != alarm.state())
                            save(
                                    alarm,
                                    next.state(),
                                    new Mutation(
                                            next.state().closedAt() == null
                                                    ? "VERIFY_PROGRESS"
                                                    : "CLOSE",
                                            null,
                                            null,
                                            null,
                                            now));
                    }
                    return null;
                });
    }

    private Alarm load(Long id) {
        Checks.required(id, "alarmId");
        Checks.positive(id, "alarmId");
        var alarm = alarms.lock(id);
        if (alarm == null) throw Checks.missing("ALARM_NOT_FOUND");
        return alarm;
    }

    private Long save(Alarm before, AlarmWorkflowState after, Mutation mutation) {
        Long actionId = alarms.save(before, after, mutation);
        events.publish(
                new BEvents.AlarmChanged(
                        BEvents.id(),
                        sequence.incrementAndGet(),
                        before.id(),
                        mutation.type(),
                        AlarmGrade.valueOf(before.grade().name()),
                        AlarmProcessStatus.valueOf(after.processStatus().name()),
                        mutation.at()));
        events.publish(
                new BEvents.AlarmCounts(
                        BEvents.id(),
                        sequence.incrementAndGet(),
                        alarms.unacknowledgedCount(),
                        mutation.at()));
        events.publish(
                new BEvents.EffectChanged(
                        BEvents.id(),
                        sequence.incrementAndGet(),
                        before.id(),
                        EffectStatus.valueOf(after.effectStatus().name()),
                        after.currentProductionCount(),
                        Math.max(1, after.targetProductionCount()),
                        mutation.at()));
        return actionId;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}

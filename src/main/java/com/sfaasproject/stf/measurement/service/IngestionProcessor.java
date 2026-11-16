package com.sfaasproject.stf.measurement.service;

import com.sfaasproject.stf.alarm.service.AlarmDecisionService;
import com.sfaasproject.stf.alarm.service.AlarmEvaluationResult;
import com.sfaasproject.stf.alarm.service.AlarmEvaluationState;
import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.alarm.service.AlarmReading;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService;
import com.sfaasproject.stf.measurement.dto.IngestionAccepted;
import com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest;
import com.sfaasproject.stf.measurement.repository.IngestionStore;
import com.sfaasproject.stf.measurement.repository.IngestionStore.DataItem;
import com.sfaasproject.stf.measurement.repository.IngestionStore.Latest;
import com.sfaasproject.stf.measurement.repository.IngestionStore.OpenAlarm;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Backend ingestion orchestration. Instantiate behind a DB transaction when persistence is ready. */
public class IngestionProcessor {
    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Seoul");
    private final IngestionStore store;
    private final IngestionMessageGuard guard;
    private final IngestionValidationService validator;
    private final ProductionCounterService production;
    private final AlarmDecisionService alarms;
    private final EquipmentStatusDecisionService equipmentStatuses;
    private final IntSupplier requiredAlarmStreak;
    private final Supplier<Duration> communicationTimeout;

    public IngestionProcessor(IngestionStore store, IngestionMessageGuard guard,
            IngestionValidationService validator, ProductionCounterService production,
            AlarmDecisionService alarms, EquipmentStatusDecisionService equipmentStatuses) {
        this(store, guard, validator, production, alarms, equipmentStatuses,
                () -> 3, () -> Duration.ofSeconds(30));
    }

    public IngestionProcessor(IngestionStore store, IngestionMessageGuard guard,
            IngestionValidationService validator, ProductionCounterService production,
            AlarmDecisionService alarms, EquipmentStatusDecisionService equipmentStatuses,
            IntSupplier requiredAlarmStreak, Supplier<Duration> communicationTimeout) {
        this.store = store;
        this.guard = guard;
        this.validator = validator;
        this.production = production;
        this.alarms = alarms;
        this.equipmentStatuses = equipmentStatuses;
        this.requiredAlarmStreak = requiredAlarmStreak;
        this.communicationTimeout = communicationTimeout;
    }

    public IngestionAccepted process(MeasurementIngestionRequest request, OffsetDateTime receivedAt) {
        if (request == null || receivedAt == null || request.messageId() == null) {
            throw new IllegalArgumentException("Message and received time are required");
        }
        IngestionMessageGuard.Acquisition acquisition = guard.acquire(request.messageId());
        if (acquisition == IngestionMessageGuard.Acquisition.COMPLETED) {
            return new IngestionAccepted(request.messageId(), receivedAt, true);
        }
        if (acquisition == IngestionMessageGuard.Acquisition.IN_PROGRESS) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                    "메시지가 처리 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        try {
            IngestionAccepted accepted = processAcquired(request, receivedAt);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        guard.completed(request.messageId());
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) guard.release(request.messageId());
                    }
                });
            } else {
                guard.completed(request.messageId());
            }
            return accepted;
        } catch (RuntimeException ex) {
            guard.release(request.messageId());
            throw ex;
        }
    }

    private IngestionAccepted processAcquired(MeasurementIngestionRequest request,
            OffsetDateTime receivedAt) {
        var equipment = store.findActiveEquipment(request.equipmentCode());
        if (equipment == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "설비를 찾을 수 없습니다.");
        }
        List<DataItem> items = validator.validate(request, equipment.code(),
                store.findActiveItems(equipment.id()));
        LocalDateTime measuredAt = request.measuredAt().atZoneSameInstant(DB_ZONE).toLocalDateTime();
        LocalDateTime localReceivedAt = receivedAt.atZoneSameInstant(DB_ZONE).toLocalDateTime();
        var pending = new ArrayList<Pending>();
        var seen = new HashMap<Long, Latest>();
        int produced = 0;
        Boolean runSignal = null;
        for (int i = 0; i < items.size(); i++) {
            DataItem item = items.get(i);
            var value = request.values().get(i);
            if (store.measurementExists(item.id(), measuredAt)) {
                throw new DomainException(HttpStatus.CONFLICT, "VALIDATION_ERROR",
                        "이미 저장된 시각의 측정값이 있습니다.");
            }
            Latest previous = store.findLatestForUpdate(item.id());
            OpenAlarm open = store.findOpenAlarmForUpdate(item.id());
            boolean latest = previous == null || measuredAt.isAfter(previous.measuredAt());
            AlarmEvaluationResult evaluation = latest ? alarms.evaluate(reading(item, measuredAt,
                    value.value()), state(previous, open), requiredAlarmStreak.getAsInt()) : null;
            if (latest && "PRODUCTION".equals(item.role())) {
                produced = Math.addExact(produced, production.quantity(
                        previous == null ? null : previous.value(),
                        previous == null ? null : previous.measuredAt(), value.value(), measuredAt,
                        item.resetTime()));
            }
            if (latest && "RUN_SIGNAL".equals(item.role())) {
                runSignal = value.value().compareTo(BigDecimal.ONE) == 0;
            }
            pending.add(new Pending(item, value.value(), open, latest, evaluation));
            seen.put(item.id(), previous);
        }
        // No writes occur until every value in the batch has passed validation and calculations.
        for (Pending p : pending) {
            store.insertMeasurement(p.item().id(), measuredAt, p.value(), localReceivedAt);
            if (!p.latest()) continue;
            AlarmEvaluationState state = p.evaluation().state();
            String valueGrade = p.evaluation().valueGrade() == null ? "NORMAL"
                    : p.evaluation().valueGrade().name();
            store.saveLatest(p.item().id(), p.value(), measuredAt, localReceivedAt, valueGrade,
                    state.outStreak(), state.criticalStreak(), state.normalStreak());
            if (p.evaluation().transition() != AlarmEvaluationResult.Transition.NONE) {
                store.applyAlarm(equipment, p.item(), p.value(), measuredAt,
                        p.open(), p.evaluation());
            }
        }
        if (produced > 0) {
            store.addProduction(equipment.id(), measuredAt.withSecond(0).withNano(0), produced);
        }
        if (runSignal == null) runSignal = store.findRunSignal(equipment.id());
        var status = equipmentStatuses.decide(localReceivedAt, localReceivedAt,
                store.hasAbnormalOpenAlarmGrade(equipment.id(), AlarmGrade.CRITICAL),
                store.hasAbnormalOpenAlarmGrade(equipment.id(), AlarmGrade.CAUTION), runSignal,
                communicationTimeout.get());
        store.saveEquipmentStatus(equipment.id(), status, localReceivedAt, localReceivedAt);
        return new IngestionAccepted(request.messageId(), receivedAt, false);
    }

    private AlarmReading reading(DataItem item, LocalDateTime measuredAt, BigDecimal value) {
        return new AlarmReading(item.id(), measuredAt, AlarmReading.Kind.valueOf(item.kind()),
                value, item.direction() == null ? null
                        : AlarmReading.Direction.valueOf(item.direction()),
                item.cautionLimit(), item.criticalLimit(), item.alarmStateValue(),
                item.alarmStateGrade());
    }

    private AlarmEvaluationState state(Latest latest, OpenAlarm open) {
        return new AlarmEvaluationState(latest == null ? null : latest.measuredAt(),
                latest == null ? 0 : latest.outStreak(),
                latest == null ? 0 : latest.criticalStreak(),
                latest == null ? 0 : latest.normalStreak(),
                open == null ? null : new AlarmEvaluationState.OpenAlarm(open.grade(),
                        open.valueRecovered()));
    }

    private record Pending(DataItem item, BigDecimal value, OpenAlarm open,
            boolean latest, AlarmEvaluationResult evaluation) { }
}

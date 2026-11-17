package com.sfaasproject.stf.operations.scheduler;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.*;
import java.util.concurrent.atomic.AtomicLong;

public class BJobs {
    private final Alarms alarms;
    private final Settings settings;
    private final Duties duties;
    private final InventoryRuntime inventory;
    private final UnitOfWork tx;
    private final BEventPublisher events;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public BJobs(
            Alarms alarms,
            Settings settings,
            Duties duties,
            InventoryRuntime inventory,
            UnitOfWork tx,
            BEventPublisher events,
            Clock clock) {
        this.alarms = alarms;
        this.settings = settings;
        this.duties = duties;
        this.inventory = inventory;
        this.tx = tx;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${stf.backend-b.reminder-delay:10000}")
    public void remindUnacknowledged() {
        var now = OffsetDateTime.now(clock);
        for (long id :
                alarms.unacknowledgedBefore(
                        now.minusMinutes(settings.integer("RENOTIFY_MIN", 3)))) {
            tx.execute(
                    () -> {
                        var alarm = alarms.lock(id);
                        if (alarm != null
                                && alarm.state().processStatus()
                                        == com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState
                                                .ProcessStatus.UNACKNOWLEDGED
                                && alarms.markReminder(id, now)) {
                            events.publish(
                                    new BEvents.AlarmChanged(
                                            BEvents.id(),
                                            sequence.incrementAndGet(),
                                            id,
                                            "RENOTIFY",
                                            AlarmGrade.valueOf(alarm.grade().name()),
                                            AlarmProcessStatus.UNACKNOWLEDGED,
                                            now));
                        }
                        return null;
                    });
        }
    }

    @Scheduled(fixedDelayString = "${stf.backend-b.inventory-delay:10000}")
    public void refreshInventory() {
        var now = OffsetDateTime.now(clock);
        tx.execute(
                () -> {
                    for (var line : inventory.recalculate(now)) {
                        var forecast =
                                line.consumptionPerMinute().subtract(line.productionPerMinute());
                        java.math.BigDecimal remaining =
                                forecast.signum() > 0
                                        ? java.math.BigDecimal.valueOf(line.quantity())
                                                .divide(forecast, 4, java.math.RoundingMode.HALF_UP)
                                        : null;
                        var stage =
                                remaining == null
                                        ? InventoryStage.MAINTAIN
                                        : remaining.compareTo(
                                                                java.math.BigDecimal.valueOf(
                                                                        settings.integer(
                                                                                "STOCK_DANGER_MIN",
                                                                                10)))
                                                        <= 0
                                                ? InventoryStage.DANGER
                                                : remaining.compareTo(
                                                                        java.math.BigDecimal
                                                                                .valueOf(
                                                                                        settings
                                                                                                .integer(
                                                                                                        "STOCK_LEVEL_MIN",
                                                                                                        30)))
                                                                <= 0
                                                        ? InventoryStage.CAUTION
                                                        : InventoryStage.MAINTAIN;
                        events.publish(
                                new BEvents.InventoryChanged(
                                        BEvents.id(),
                                        sequence.incrementAndGet(),
                                        line.lineId(),
                                        line.quantity(),
                                        stage,
                                        remaining,
                                        now));
                    }
                    return null;
                });
    }

    @Scheduled(fixedDelayString = "${stf.backend-b.duty-delay:60000}")
    public void transitionDuty() {
        var today = LocalDate.now(clock);
        var now = OffsetDateTime.now(clock);
        tx.execute(
                () -> {
                    for (var duty : duties.schedules()) {
                        if (!today.isBefore(duty.from())
                                && !today.isAfter(duty.to())
                                && duties.claimTransition(today, duty.id()))
                            events.publish(
                                    new BEvents.DutyChanged(
                                            BEvents.id(), duty.id(), duty.userId(), now));
                    }
                    return null;
                });
    }
}

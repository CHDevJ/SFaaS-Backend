package com.sfaasproject.stf.operations.scheduler;

import com.sfaasproject.stf.inventory.service.V7InventoryService;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.BPorts.UnitOfWork;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicLong;

/** v7 recalculates every 60 seconds and immediately after committed equipment state changes. */
public class V7ForecastJobs {
    private final V7InventoryService inventory;
    private final UnitOfWork tx;
    private final BEventPublisher events;
    private final com.sfaasproject.stf.equipment.service.V7EquipmentAlarmBridge equipment;
    private final AtomicLong sequence = new AtomicLong();

    public V7ForecastJobs(
            V7InventoryService inventory,
            UnitOfWork tx,
            BEventPublisher events,
            com.sfaasproject.stf.equipment.service.V7EquipmentAlarmBridge equipment) {
        this.inventory = inventory;
        this.tx = tx;
        this.events = events;
        this.equipment = equipment;
    }

    @Scheduled(fixedDelayString = "${stf.backend-b.communication-delay:1000}")
    public void refreshCommunication() {
        equipment.refreshCommunication();
    }

    @Scheduled(fixedDelayString = "${stf.backend-b.inventory-delay:60000}")
    public void refresh() {
        tx.execute(
                () -> {
                    var snapshot = inventory.snapshot();
                    for (var line : snapshot.inventory())
                        events.publish(
                                new V7Events.InventoryRecalculated(
                                        BEvents.id(),
                                        sequence.incrementAndGet(),
                                        line.lineId(),
                                        line.quantity(),
                                        line.stage() == null ? null : line.stage().name(),
                                        line.remainingMinutes(),
                                        snapshot.calculatedAt(),
                                        line.stageThresholds()));
                    return null;
                });
    }

    @EventListener
    public void stateChanged(V7Events.EquipmentCommitted e) {
        refresh();
    }
}

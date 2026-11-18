package com.sfaasproject.stf.equipment.service;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.measurement.repository.JdbcV7IngestionStore;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.operations.event.V7Events;
import com.sfaasproject.stf.operations.port.BPorts.UnitOfWork;
import com.sfaasproject.stf.operations.port.V7Context;

import org.springframework.context.event.EventListener;

import java.time.*;

public class V7EquipmentAlarmBridge {
    private final JdbcBDatabase db;
    private final com.sfaasproject.stf.alarm.port.AlarmQuery alarms;
    private final JdbcV7IngestionStore store;
    private final V7Context context;
    private final UnitOfWork tx;

    public V7EquipmentAlarmBridge(
            JdbcBDatabase db,
            com.sfaasproject.stf.alarm.port.AlarmQuery alarms,
            JdbcV7IngestionStore store,
            V7Context context,
            UnitOfWork tx) {
        this.db = db;
        this.alarms = alarms;
        this.store = store;
        this.context = context;
        this.tx = tx;
    }

    @EventListener
    public void changed(V7Events.AlarmCommitted event) {
        tx.execute(
                () -> {
                    var alarm = alarms.find(event.alarmId());
                    if (alarm == null) return null;
                    refreshEquipment(alarm.equipmentId());
                    return null;
                });
    }

    public void refreshCommunication() {
        for (var equipment : context.equipment())
            if (!equipment.deleted())
                tx.execute(
                        () -> {
                            refreshEquipment(equipment.id());
                            return null;
                        });
    }

    private void refreshEquipment(long equipment) {
        context.lockEquipment(equipment);
        var state =
                db.one(
                        "SELECT status,last_received_at FROM equipment_state WHERE equipment_id=?"
                            + " FOR UPDATE",
                        equipment);
        if (state == null) return;
        var received = dateTime(state.get("last_received_at"));
        var now = db.now();
        var status =
                new EquipmentStatusDecisionService()
                        .decide(
                                received == null ? null : received.toLocalDateTime(),
                                now.toLocalDateTime(),
                                alarms.abnormal(equipment, AlarmGrade.CRITICAL),
                                alarms.abnormal(equipment, AlarmGrade.CAUTION),
                                store.findRunSignal(equipment),
                                Duration.ofSeconds(context.integer("COMM_TIMEOUT_SEC", 30)));
        if (!status.name().equals(state.get("status")))
            store.saveEquipmentStatus(
                    equipment,
                    status,
                    now.atZoneSameInstant(com.sfaasproject.stf.operations.service.V7Support.KST)
                            .toLocalDateTime(),
                    received == null ? null : received.toLocalDateTime());
    }
}

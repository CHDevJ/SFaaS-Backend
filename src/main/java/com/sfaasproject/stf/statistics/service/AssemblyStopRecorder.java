package com.sfaasproject.stf.statistics.service;

import com.sfaasproject.stf.operations.event.V7Events;
import com.sfaasproject.stf.operations.port.BPorts.UnitOfWork;
import com.sfaasproject.stf.statistics.port.AssemblyStopObservationQuery;
import com.sfaasproject.stf.statistics.repository.AssemblyStopRepository;

import org.springframework.context.event.EventListener;

import java.math.BigDecimal;
import java.util.List;

/** ERD E2: actual stock depletion or assembly FAULT. Forecasts never create actual stops. */
public class AssemblyStopRecorder {
    private final AssemblyStopObservationQuery query;
    private final AssemblyStopRepository repository;
    private final ActualLossCalculator loss;
    private final UnitOfWork transactions;

    public AssemblyStopRecorder(
            AssemblyStopObservationQuery query,
            AssemblyStopRepository repository,
            ActualLossCalculator loss,
            UnitOfWork transactions) {
        this.query = query;
        this.repository = repository;
        this.loss = loss;
        this.transactions = transactions;
    }

    public void refresh() {
        transactions.execute(
                () -> {
                    repository.lock();
                    var observation = query.observe();
                    var open = repository.open();
                    if (!observation.causes().isEmpty()) {
                        if (open.isEmpty()
                                && observation.hourlyOutput() != null
                                && observation.hourlyOutput() > 0
                                && observation.unitPrice() != null
                                && observation.unitPrice() >= 0)
                            repository.start(
                                    observation.causes().getFirst(),
                                    observation.at(),
                                    observation.hourlyOutput(),
                                    observation.unitPrice());
                    } else if (observation.recoveryConfirmed()) {
                        for (var stop : open) {
                            var end = observation.at().withNano(0);
                            if (end.isBefore(stop.startedAt())) continue;
                            var amount =
                                    loss.calculate(
                                            List.of(
                                                    new ActualLossCalculator.Interval(
                                                            stop.startedAt(),
                                                            end,
                                                            BigDecimal.valueOf(stop.hourlyOutput()),
                                                            BigDecimal.valueOf(stop.unitPrice()))));
                            repository.end(stop, end, amount);
                        }
                    }
                    return null;
                });
    }

    @EventListener
    public void equipmentChanged(V7Events.EquipmentCommitted event) {
        refresh();
    }

    @EventListener
    public void inventoryChanged(V7Events.InventoryRecalculated event) {
        refresh();
    }

    @EventListener
    public void alarmChanged(V7Events.AlarmCommitted event) {
        refresh();
    }
}

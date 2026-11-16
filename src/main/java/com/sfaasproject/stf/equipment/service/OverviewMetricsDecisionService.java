package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.ProcessStatus;
import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Counts the snapshot fields used by the overview query. */
@Service
public class OverviewMetricsDecisionService {
    public record Equipment(long id, Status status) { }
    public record Alarm(long id, ProcessStatus processStatus) { }
    public record Metrics(int connectedEquipmentCount, int activeAlarmCount,
            int unacknowledgedAlarmCount) { }

    public Metrics count(List<Equipment> equipment, List<Alarm> alarms) {
        if (equipment == null || alarms == null) throw new IllegalArgumentException("Snapshots are required");
        Set<Long> equipmentIds = equipment.stream().map(Equipment::id)
                .collect(java.util.stream.Collectors.toSet());
        int connected = (int) equipment.stream()
                .filter(e -> e.status() != Status.COMMUNICATION_LOST).count();
        int active = (int) alarms.stream()
                .filter(a -> a.processStatus() != ProcessStatus.CLOSED).count();
        int unacknowledged = (int) alarms.stream()
                .filter(a -> a.processStatus() == ProcessStatus.UNACKNOWLEDGED).count();
        return new Metrics(connected, active, unacknowledged);
    }
}

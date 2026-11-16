package com.sfaasproject.stf.equipment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState.ProcessStatus;
import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverviewMetricsDecisionServiceTest {
    private final OverviewMetricsDecisionService service = new OverviewMetricsDecisionService();

    @Test
    void countsConnectedEquipmentAndOnlyOpenAlarms() {
        var result = service.count(List.of(
                new OverviewMetricsDecisionService.Equipment(1, Status.RUNNING),
                new OverviewMetricsDecisionService.Equipment(2, Status.COMMUNICATION_LOST)),
                List.of(new OverviewMetricsDecisionService.Alarm(10, ProcessStatus.UNACKNOWLEDGED),
                        new OverviewMetricsDecisionService.Alarm(11, ProcessStatus.ACTION),
                        new OverviewMetricsDecisionService.Alarm(12, ProcessStatus.CLOSED)));
        assertEquals(new OverviewMetricsDecisionService.Metrics(1, 2, 1), result);
    }
}

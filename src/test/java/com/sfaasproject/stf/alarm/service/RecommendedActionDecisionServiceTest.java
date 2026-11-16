package com.sfaasproject.stf.alarm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sfaasproject.stf.alarm.service.RecommendedActionDecisionService.PastAction;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendedActionDecisionServiceTest {
    private final RecommendedActionDecisionService service = new RecommendedActionDecisionService();

    @Test
    void higherEffectivenessRanksBeforeMoreFrequentButLessEffectiveAction() {
        var result = service.rank(List.of(
                new PastAction("베어링 교체", true, 20),
                new PastAction("베어링 교체", true, 40),
                new PastAction("센서 청소", true, 10),
                new PastAction("센서 청소", false, 30),
                new PastAction("센서 청소", false, 20)));
        assertEquals("베어링 교체", result.get(0).actionContent());
        assertEquals(2, result.get(0).effectiveCount());
        assertEquals(100.0, result.get(0).effectivenessRate());
        assertEquals(30.0, result.get(0).averageRepairMinutes());
        assertEquals("센서 청소", result.get(1).actionContent());
    }
}

package com.sfaasproject.stf.statistics.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquipmentRankingDecisionServiceTest {
    private final EquipmentRankingDecisionService service = new EquipmentRankingDecisionService();

    @Test
    void sortsEachContractFieldDescendingAndBreaksTiesById() {
        var a = new EquipmentRankingDecisionService.Equipment(2, "A", 5,
                new BigDecimal("30"), new BigDecimal("10"), new BigDecimal("100"));
        var b = new EquipmentRankingDecisionService.Equipment(1, "B", 2,
                new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("200"));
        var c = new EquipmentRankingDecisionService.Equipment(3, "C", 5,
                new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("50"));
        var items = List.of(a, b, c);
        assertEquals(List.of(2L, 3L, 1L), service.rank(items,
                EquipmentRankingDecisionService.SortBy.faultCount).stream()
                .map(r -> r.equipment().id()).toList());
        assertEquals(1L, service.rank(items,
                EquipmentRankingDecisionService.SortBy.downtimeMinutes).get(0).equipment().id());
        assertEquals(3L, service.rank(items,
                EquipmentRankingDecisionService.SortBy.averageRepairMinutes).get(0).equipment().id());
        assertEquals(1L, service.rank(items,
                EquipmentRankingDecisionService.SortBy.lossAmount).get(0).equipment().id());
        assertEquals(1, service.rank(items,
                EquipmentRankingDecisionService.SortBy.lossAmount).get(0).rank());
    }

    @Test
    void rejectsNegativeMetrics() {
        var invalid = new EquipmentRankingDecisionService.Equipment(1, "A", 0,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"));
        assertThrows(IllegalArgumentException.class, () -> service.rank(List.of(invalid),
                EquipmentRankingDecisionService.SortBy.lossAmount));
    }
}

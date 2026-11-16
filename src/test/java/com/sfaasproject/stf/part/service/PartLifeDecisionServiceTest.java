package com.sfaasproject.stf.part.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sfaasproject.stf.part.service.PartLifeDecisionService.Basis;
import com.sfaasproject.stf.part.service.PartLifeDecisionService.RunningInterval;
import com.sfaasproject.stf.part.service.PartLifeDecisionService.Status;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartLifeDecisionServiceTest {
    private final PartLifeDecisionService service = new PartLifeDecisionService();
    private final LocalDateTime installed = LocalDateTime.of(2026, 11, 1, 0, 0);

    @Test
    void elapsedDaysReachCriterionOnExactDate() {
        var before = service.calculate(Basis.ELAPSED_DAYS, installed, 10,
                List.of(), installed.plusDays(8), new BigDecimal("80"));
        assertEquals(new BigDecimal("8.0000"), before.used());
        assertEquals(Status.DUE_SOON, before.status());
        assertEquals(installed.plusDays(10), before.expectedCriterionAt());
        var overdue = service.calculate(Basis.ELAPSED_DAYS, installed, 10,
                List.of(), installed.plusDays(10), new BigDecimal("80"));
        assertEquals(Status.OVERDUE, overdue.status());
    }

    @Test
    void operatingHoursCountOnlyRunningTimeSinceReplacement() {
        LocalDateTime replaced = installed.plusDays(1);
        var usage = service.calculate(Basis.OPERATING_HOURS, replaced, 10,
                List.of(new RunningInterval(installed, replaced.plusHours(3)),
                        new RunningInterval(replaced.plusHours(5), replaced.plusHours(7))),
                replaced.plusHours(8), new BigDecimal("80"));
        assertEquals(new BigDecimal("5.0000"), usage.used());
        assertEquals(new BigDecimal("5.0000"), usage.remaining());
        assertEquals(Status.NORMAL, usage.status());
        assertNull(usage.expectedCriterionAt());
    }
    @org.junit.jupiter.api.Test
    void fractionalDayCriterionMatchesOpenApiNumberType() {
        var start = java.time.LocalDateTime.of(2026, 11, 17, 9, 0);
        var usage = service.calculate(PartLifeDecisionService.Basis.ELAPSED_DAYS,
                start, new java.math.BigDecimal("1.5"), java.util.List.of(),
                start.plusDays(1), new java.math.BigDecimal("80"));
        org.junit.jupiter.api.Assertions.assertEquals(start.plusHours(36),
                usage.expectedCriterionAt());
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.math.BigDecimal("1.5"), usage.criterion());
    }}

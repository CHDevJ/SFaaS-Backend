package com.sfaasproject.stf.statistics.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Sorts pre-aggregated equipment statistics using the OpenAPI sortBy contract. */
@Service
public class EquipmentRankingDecisionService {
    public enum SortBy { faultCount, downtimeMinutes, averageRepairMinutes, lossAmount }
    public record Equipment(long id, String name, int faultCount, BigDecimal downtimeMinutes,
            BigDecimal averageRepairMinutes, BigDecimal lossAmount) { }
    public record Ranked(int rank, Equipment equipment) { }

    public List<Ranked> rank(List<Equipment> equipment, SortBy sortBy) {
        if (equipment == null || sortBy == null) throw new IllegalArgumentException("Ranking inputs are required");
        for (Equipment item : equipment) {
            if (item == null || item.id() <= 0 || item.name() == null || item.name().isBlank()
                    || item.faultCount() < 0 || negative(item.downtimeMinutes())
                    || negative(item.averageRepairMinutes()) || negative(item.lossAmount())) {
                throw new IllegalArgumentException("Invalid equipment ranking metrics");
            }
        }
        Comparator<Equipment> order = switch (sortBy) {
            case faultCount -> Comparator.comparingInt(Equipment::faultCount);
            case downtimeMinutes -> Comparator.comparing(Equipment::downtimeMinutes);
            case averageRepairMinutes -> Comparator.comparing(Equipment::averageRepairMinutes);
            case lossAmount -> Comparator.comparing(Equipment::lossAmount);
        };
        List<Equipment> sorted = equipment.stream()
                .sorted(order.reversed().thenComparingLong(Equipment::id)).toList();
        java.util.ArrayList<Ranked> result = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) result.add(new Ranked(i + 1, sorted.get(i)));
        return List.copyOf(result);
    }

    private boolean negative(BigDecimal value) {
        return value == null || value.signum() < 0;
    }
}

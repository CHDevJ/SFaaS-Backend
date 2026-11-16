package com.sfaasproject.stf.alarm.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Ranks already-matched historical actions by observed effectiveness. */
@Service
public class RecommendedActionDecisionService {
    public record PastAction(String actionContent, boolean effective, long repairMinutes) { }
    public record Recommendation(String actionContent, int effectiveCount, int totalCount,
            double effectivenessRate, double averageRepairMinutes) { }

    public List<Recommendation> rank(List<PastAction> matchingRecentActions) {
        if (matchingRecentActions == null) throw new IllegalArgumentException("Actions are required");
        Map<String, List<PastAction>> groups = matchingRecentActions.stream()
                .filter(a -> a.actionContent() != null && !a.actionContent().isBlank())
                .collect(Collectors.groupingBy(PastAction::actionContent));
        return groups.entrySet().stream().map(group -> {
            List<PastAction> actions = group.getValue();
            int effective = (int) actions.stream().filter(PastAction::effective).count();
            double averageMinutes = actions.stream().mapToLong(PastAction::repairMinutes)
                    .average().orElse(0);
            return new Recommendation(group.getKey(), effective, actions.size(),
                    100.0 * effective / actions.size(), averageMinutes);
        }).sorted(Comparator.comparingDouble(Recommendation::effectivenessRate).reversed()
                .thenComparing(Comparator.comparingInt(Recommendation::totalCount).reversed())
                .thenComparingDouble(Recommendation::averageRepairMinutes)
                .thenComparing(Recommendation::actionContent)).toList();
    }
}

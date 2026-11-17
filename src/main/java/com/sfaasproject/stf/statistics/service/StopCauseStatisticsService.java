package com.sfaasproject.stf.statistics.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/** STA-010: clip to [from,to), then attribute each instant at most once per equipment. */
@Service
public class StopCauseStatisticsService {
    public enum Source {
        ALARM,
        COMMUNICATION_LOST
    }

    public record Stop(
            long equipmentId,
            long sourceId,
            Source source,
            String cause,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt) {}

    public record Cause(String cause, Source source, BigDecimal downtimeMinutes) {}

    private record Key(String cause, Source source) {}

    public List<Cause> summarize(List<Stop> stops, OffsetDateTime from, OffsetDateTime to) {
        if (stops == null || from == null || to == null || !to.isAfter(from))
            throw new IllegalArgumentException("A nonempty half-open query period is required");
        Map<Long, List<Stop>> byEquipment = new HashMap<>();
        for (var s : stops) {
            if (s == null
                    || s.equipmentId() <= 0
                    || s.sourceId() <= 0
                    || s.source() == null
                    || s.cause() == null
                    || s.cause().isBlank()
                    || s.startedAt() == null
                    || s.endedAt() != null && s.endedAt().isBefore(s.startedAt()))
                throw new IllegalArgumentException("Invalid stop interval");
            if (s.startedAt().isBefore(to) && (s.endedAt() == null || s.endedAt().isAfter(from)))
                byEquipment.computeIfAbsent(s.equipmentId(), id -> new ArrayList<>()).add(s);
        }
        Map<Key, BigDecimal> nanos = new HashMap<>();
        var priority =
                Comparator.comparingInt((Stop s) -> s.source() == Source.COMMUNICATION_LOST ? 0 : 1)
                        .thenComparing(Stop::startedAt)
                        .thenComparingLong(Stop::sourceId);
        for (var equipment : byEquipment.values()) {
            var boundaries =
                    new TreeSet<OffsetDateTime>(Comparator.comparing(OffsetDateTime::toInstant));
            boundaries.add(from);
            boundaries.add(to);
            for (var s : equipment) {
                if (s.startedAt().isAfter(from) && s.startedAt().isBefore(to))
                    boundaries.add(s.startedAt());
                if (s.endedAt() != null && s.endedAt().isAfter(from) && s.endedAt().isBefore(to))
                    boundaries.add(s.endedAt());
            }
            var times = new ArrayList<>(boundaries);
            for (int i = 1; i < times.size(); i++) {
                var start = times.get(i - 1);
                var end = times.get(i);
                var owner =
                        equipment.stream()
                                .filter(
                                        s ->
                                                !s.startedAt().isAfter(start)
                                                        && (s.endedAt() == null
                                                                || s.endedAt().isAfter(start)))
                                .min(priority)
                                .orElse(null);
                if (owner != null)
                    nanos.merge(
                            new Key(owner.cause(), owner.source()),
                            BigDecimal.valueOf(Duration.between(start, end).toNanos()),
                            BigDecimal::add);
            }
        }
        return nanos.entrySet().stream()
                .map(
                        e ->
                                new Cause(
                                        e.getKey().cause(),
                                        e.getKey().source(),
                                        e.getValue()
                                                .divide(
                                                        BigDecimal.valueOf(60_000_000_000L),
                                                        12,
                                                        RoundingMode.HALF_UP)))
                .sorted(
                        Comparator.comparing(Cause::downtimeMinutes)
                                .reversed()
                                .thenComparing(Cause::source)
                                .thenComparing(Cause::cause))
                .toList();
    }
}

package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService.Status;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Supplies the state row and history boundary to persist when status changes. */
@Service
public class EquipmentStatusTransitionService {
    public record Snapshot(Status status, LocalDateTime statusSince, LocalDateTime lastReceivedAt) { }
    public record HistoryChange(Status closedStatus, LocalDateTime closedAt,
            Status openedStatus, LocalDateTime openedAt) { }
    public record Decision(Snapshot state, HistoryChange historyChange) { }

    public Decision transition(Snapshot previous, Status nextStatus, LocalDateTime now,
            LocalDateTime receivedAt) {
        Objects.requireNonNull(nextStatus);
        Objects.requireNonNull(now);
        if (previous != null && previous.statusSince() != null
                && now.isBefore(previous.statusSince())) {
            throw new IllegalArgumentException("Status time cannot move backwards");
        }
        LocalDateTime latestReceived = receivedAt;
        if (previous != null && previous.lastReceivedAt() != null
                && (latestReceived == null || previous.lastReceivedAt().isAfter(latestReceived))) {
            latestReceived = previous.lastReceivedAt();
        }
        if (previous != null && previous.status() == nextStatus) {
            return new Decision(new Snapshot(nextStatus, previous.statusSince(), latestReceived), null);
        }
        HistoryChange change = new HistoryChange(previous == null ? null : previous.status(),
                previous == null ? null : now, nextStatus, now);
        return new Decision(new Snapshot(nextStatus, now, latestReceived), change);
    }
}

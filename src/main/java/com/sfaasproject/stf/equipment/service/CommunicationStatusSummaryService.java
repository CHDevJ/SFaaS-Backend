package com.sfaasproject.stf.equipment.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

/** COM-002 header state from equipment communication snapshots. */
@Service
public class CommunicationStatusSummaryService {
    public enum Status { ALL_NORMAL, PARTIAL_DISCONNECTED, ALL_DISCONNECTED }
    public record Equipment(long id, boolean disconnected, LocalDateTime disconnectedSince) { }
    public record Summary(Status status, int totalEquipmentCount,
            int disconnectedEquipmentCount, LocalDateTime allDisconnectedSince) { }

    public Summary summarize(List<Equipment> equipment) {
        if (equipment == null) throw new IllegalArgumentException("Equipment is required");
        var ids = new HashSet<Long>();
        int disconnected = 0;
        LocalDateTime allSince = null;
        for (Equipment item : equipment) {
            if (item == null || item.id() <= 0 || !ids.add(item.id())
                    || item.disconnected() && item.disconnectedSince() == null) {
                throw new IllegalArgumentException("Invalid communication snapshot");
            }
            if (item.disconnected()) {
                disconnected++;
                if (allSince == null || item.disconnectedSince().isAfter(allSince)) {
                    allSince = item.disconnectedSince();
                }
            }
        }
        int total = equipment.size();
        Status status = disconnected == 0 ? Status.ALL_NORMAL
                : disconnected == total ? Status.ALL_DISCONNECTED : Status.PARTIAL_DISCONNECTED;
        return new Summary(status, total, disconnected,
                status == Status.ALL_DISCONNECTED ? allSince : null);
    }
}

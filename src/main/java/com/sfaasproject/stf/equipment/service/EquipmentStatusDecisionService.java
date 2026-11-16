package com.sfaasproject.stf.equipment.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** SF5-23 priority: disconnected, fault, caution, idle, running. */
@Service
public class EquipmentStatusDecisionService {
    public enum Status { RUNNING, IDLE, CAUTION, FAULT, COMMUNICATION_LOST }

    public Status decide(LocalDateTime lastReceivedAt, LocalDateTime now,
            boolean hasCritical, boolean hasCaution, Boolean runSignal) {
        return decide(lastReceivedAt, now, hasCritical, hasCaution, runSignal,
                Duration.ofSeconds(30));
    }

    public Status decide(LocalDateTime lastReceivedAt, LocalDateTime now,
            boolean hasCritical, boolean hasCaution, Boolean runSignal, Duration timeout) {
        Objects.requireNonNull(now, "now");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Communication timeout must be positive");
        }
        if (lastReceivedAt == null || Duration.between(lastReceivedAt, now).compareTo(timeout) > 0) {
            return Status.COMMUNICATION_LOST;
        }
        if (hasCritical) return Status.FAULT;
        if (hasCaution) return Status.CAUTION;
        return Boolean.TRUE.equals(runSignal) ? Status.RUNNING : Status.IDLE;
    }
}

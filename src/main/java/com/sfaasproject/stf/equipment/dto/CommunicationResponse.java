package com.sfaasproject.stf.equipment.dto;

import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.service.CommunicationStatusService;
import com.sfaasproject.stf.equipment.service.CommunicationStatusSummaryService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/** COM-001/002 응답 DTO (openapi CommunicationPageData, CommunicationSummary, CommunicationEquipment). */
public final class CommunicationResponse {

    private CommunicationResponse() {
    }

    /** openapi CommunicationSummary. */
    public record Summary(String status, int totalEquipmentCount, int disconnectedEquipmentCount,
            OffsetDateTime allDisconnectedSince) {

        public static Summary from(CommunicationStatusSummaryService.Summary summary) {
            return new Summary(summary.status().name(), summary.totalEquipmentCount(),
                    summary.disconnectedEquipmentCount(), offset(summary.allDisconnectedSince()));
        }
    }

    /** openapi CommunicationEquipment. */
    public record Equipment(long equipmentId, String equipmentCode, String equipmentName, String lineName,
            String communicationMethod, String status, OffsetDateTime lastReceivedAt, OffsetDateTime disconnectedSince,
            int todayDisconnectionCount, long todayDisconnectionMinutes) {

        static Equipment from(CommunicationStatusService.Item item) {
            return new Equipment(item.equipmentId(), item.equipmentCode(), item.equipmentName(), item.lineName(),
                    item.communicationMethod(), item.status(), offset(item.lastReceivedAt()),
                    offset(item.disconnectedSince()), item.todayDisconnectionCount(),
                    item.todayDisconnectionMinutes());
        }
    }

    /** openapi CommunicationPageData. */
    public record PageData(Summary summary, List<Equipment> items) {

        public static PageData from(CommunicationStatusService.EquipmentPage page) {
            return new PageData(Summary.from(page.summary()),
                    page.items().getContent().stream().map(Equipment::from).toList());
        }
    }

    private static OffsetDateTime offset(LocalDateTime local) {
        return local == null ? null : KstTime.toOffset(KstTime.toInstant(local));
    }
}

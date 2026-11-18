package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.service.CommunicationQuery.Interval;
import com.sfaasproject.stf.equipment.service.CommunicationQuery.Row;
import com.sfaasproject.stf.equipment.service.CommunicationStatusSummaryService.Summary;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COM-001 설비별 통신 현황과 COM-002 헤더 통신 상태 (openapi v7).
 *
 * <ul>
 *   <li>통신 끊김 판정은 수집 쪽 스케줄러가 equipment_state 에 남긴 COMMUNICATION_LOST 를 그대로 쓴다
 *       (마지막 수신 후 COMM_TIMEOUT_SEC 초과). 상태 줄이 없는 설비(값을 받은 적 없음)도 끊김으로 본다
 *       (EQP·OVW-001 과 같음). 끊긴 시각은 상태 시작 시각, 상태 줄이 없으면 설비 등록 시각이다.</li>
 *   <li>오늘 끊김 횟수·분은 오늘(Asia/Seoul 0시~지금)과 겹치는 COMMUNICATION_LOST 상태 이력이다.
 *       어제부터 이어진 끊김도 1회로 세고 오늘 안의 길이만 분으로 더한다(분 미만 버림).</li>
 *   <li>COM-001 summary 는 lineId 조건만 따르고 status 조건·페이지와는 상관없다. 정렬은 오늘 끊김 횟수 DESC,
 *       오늘 끊김 분 DESC, 설비 번호 ASC. 설비가 0대면 ALL_NORMAL 과 0건이다.</li>
 * </ul>
 */
@Service
public class CommunicationStatusService {

    static final String CONNECTED = "CONNECTED";
    static final String DISCONNECTED = "DISCONNECTED";

    /** COM-001 한 줄 (openapi CommunicationEquipment). */
    public record Item(long equipmentId, String equipmentCode, String equipmentName, String lineName,
            String communicationMethod, String status, LocalDateTime lastReceivedAt, LocalDateTime disconnectedSince,
            int todayDisconnectionCount, long todayDisconnectionMinutes) { }

    public record EquipmentPage(Summary summary, Page<Item> items) { }

    private final CommunicationQuery query;
    private final CommunicationStatusSummaryService summaries;
    private final Clock clock;

    public CommunicationStatusService(CommunicationQuery query, CommunicationStatusSummaryService summaries,
            Clock clock) {
        this.query = query;
        this.summaries = summaries;
        this.clock = clock;
    }

    /** COM-001. status 는 CONNECTED 또는 DISCONNECTED. */
    @Transactional(readOnly = true)
    public EquipmentPage list(Long lineId, String status, PageQuery page) {
        if (status != null && !CONNECTED.equals(status) && !DISCONNECTED.equals(status)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
        }
        List<Item> all = items(query.equipment(lineId));
        Summary summary = summarize(all);
        List<Item> filtered = all.stream().filter(item -> status == null || status.equals(item.status()))
                .sorted(Comparator.comparingInt(Item::todayDisconnectionCount).reversed()
                        .thenComparing(Comparator.comparingLong(Item::todayDisconnectionMinutes).reversed())
                        .thenComparingLong(Item::equipmentId))
                .toList();
        var pageable = page.toPageable(Sort.unsorted());
        int start = (int) Math.min(pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new EquipmentPage(summary, new PageImpl<>(filtered.subList(start, end), pageable, filtered.size()));
    }

    /** COM-002. 사용 중인 라인의 사용 중인 설비 전체. */
    @Transactional(readOnly = true)
    public Summary summary() {
        return summarize(items(query.equipment(null)));
    }

    private Summary summarize(List<Item> items) {
        return summaries.summarize(items.stream().map(item -> new CommunicationStatusSummaryService.Equipment(
                item.equipmentId(), DISCONNECTED.equals(item.status()), item.disconnectedSince())).toList());
    }

    private List<Item> items(List<Row> rows) {
        LocalDateTime now = KstTime.toLocal(clock.instant());
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        Map<Long, List<Interval>> gaps = query.disconnections(rows.stream().map(Row::equipmentId).toList(), today,
                now);
        return rows.stream().map(row -> {
            boolean disconnected = row.status() == null || "COMMUNICATION_LOST".equals(row.status());
            LocalDateTime since = !disconnected ? null : row.statusSince() != null ? row.statusSince()
                    : row.createdAt();
            List<Interval> todays = gaps.getOrDefault(row.equipmentId(), List.of());
            long seconds = todays.stream().mapToLong(gap -> {
                LocalDateTime start = gap.startedAt().isBefore(today) ? today : gap.startedAt();
                LocalDateTime end = gap.endedAt() == null || gap.endedAt().isAfter(now) ? now : gap.endedAt();
                return Math.max(0, Duration.between(start, end).getSeconds());
            }).sum();
            return new Item(row.equipmentId(), row.equipmentCode(), row.equipmentName(), row.lineName(),
                    row.communicationMethod(), disconnected ? DISCONNECTED : CONNECTED, row.lastReceivedAt(), since,
                    todays.size(), seconds / 60);
        }).toList();
    }
}

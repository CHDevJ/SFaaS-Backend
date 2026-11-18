package com.sfaasproject.stf.measurement.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.entity.DataItemEntity;
import com.sfaasproject.stf.equipment.repository.DataItemRepository;
import com.sfaasproject.stf.equipment.repository.EquipmentRepository;
import com.sfaasproject.stf.measurement.service.MeasurementTrendQuery.Alarm;
import com.sfaasproject.stf.measurement.service.MeasurementTrendQuery.Interval;
import com.sfaasproject.stf.measurement.service.MeasurementTrendQuery.Point;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MEAS-001 데이터 항목 추세 (openapi v7 + A 제안값).
 *
 * <p>집계 간격: 24시간 이하는 원본, 7일 이하는 1분, 그보다 길면 1시간에서 시작한다(v7 제안값). 항목마다 최대
 * 2000점이어야 하므로, 원본이 2000점을 넘거나 칸 수가 2000을 넘으면 {@link #LADDER} 의 다음 간격으로 올린다
 * (2026-11-18 A 제안값). 모든 항목에 같은 간격을 써서 차트 x축이 맞게 한다. 측정값은 평균, 상태값·누적값은
 * 칸의 마지막 값이고, 칸 시각은 칸의 시작(한국 시간 자정 기준으로 맞춤)이다.
 *
 * <p>통신 끊김: 설비 상태 이력의 COMMUNICATION_LOST 구간을 조회 구간으로 자르고 겹치면 합친다. 아직 끊겨 있으면
 * endedAt 은 null 이다. 차트 선이 끊기도록 각 끊김 시작 시각에 value=null 점을 넣는다.
 */
@Service
public class MeasurementTrendService {

    static final int MAX_POINTS = 2000;
    static final int MAX_ITEMS = 10;
    /** 원본 다음으로 쓰는 집계 간격(초). */
    static final List<Integer> LADDER = List.of(60, 300, 600, 900, 1800, 3600, 7200, 10800, 21600, 43200, 86400);

    public record Marker(long alarmId, LocalDateTime occurredAt, double value, String grade) { }

    public record Abnormal(LocalDateTime startedAt, LocalDateTime endedAt, String grade) { }

    public record Series(long dataItemId, String dataItemName, String valueType, String unit, Double cautionThreshold,
            Double criticalThreshold, String thresholdDirection, int decimalPlaces, String zeroDisplayName,
            String oneDisplayName, List<Point> points, List<Marker> alarmMarkers, List<Abnormal> abnormalIntervals,
            int bucketSeconds) { }

    public record Trend(long equipmentId, List<Series> series, List<Interval> disconnections,
            boolean currentlyConnected, int bucketSeconds, boolean raw) { }

    private final EquipmentRepository equipment;
    private final DataItemRepository dataItems;
    private final MeasurementTrendQuery query;

    public MeasurementTrendService(EquipmentRepository equipment, DataItemRepository dataItems,
            MeasurementTrendQuery query) {
        this.equipment = equipment;
        this.dataItems = dataItems;
        this.query = query;
    }

    @Transactional(readOnly = true)
    public Trend trend(long equipmentId, OffsetDateTime fromInput, OffsetDateTime toInput, List<Long> dataItemIds) {
        if (fromInput == null || toInput == null || !fromInput.isBefore(toInput)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "조회 기간을 확인해 주세요.");
        }
        if (dataItemIds == null || dataItemIds.isEmpty() || dataItemIds.size() > MAX_ITEMS
                || dataItemIds.stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(dataItemIds).size() != dataItemIds.size()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
        }
        equipment.findById(equipmentId).filter(e -> !e.isDeleted()).orElseThrow(() -> new DomainException(
                HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "설비를 찾을 수 없습니다."));
        Map<Long, DataItemEntity> items = dataItems.findAllById(dataItemIds).stream()
                .filter(item -> !item.isDeleted() && item.getEquipmentId() == equipmentId)
                .collect(Collectors.toMap(DataItemEntity::getId, Function.identity()));
        if (items.size() != dataItemIds.size()) {
            throw new DomainException(HttpStatus.NOT_FOUND, "DATA_ITEM_NOT_FOUND", "데이터 항목을 찾을 수 없습니다.");
        }
        LocalDateTime from = KstTime.toLocal(fromInput.toInstant());
        LocalDateTime to = KstTime.toLocal(toInput.toInstant());

        long rangeSeconds = Math.max(1, Duration.between(from, to).getSeconds());
        boolean raw = rangeSeconds <= Duration.ofHours(24).getSeconds()
                && query.counts(dataItemIds, from, to).values().stream().allMatch(count -> count <= MAX_POINTS);
        int bucket = raw ? 0 : bucketSeconds(rangeSeconds);
        LocalDateTime anchor = raw ? from : anchor(from, bucket);

        List<DataItemEntity> averaged = dataItemIds.stream().map(items::get)
                .filter(item -> DataItemEntity.MEASUREMENT.equals(item.getItemKind())).toList();
        List<DataItemEntity> last = dataItemIds.stream().map(items::get)
                .filter(item -> !DataItemEntity.MEASUREMENT.equals(item.getItemKind())).toList();
        Map<Long, List<Point>> points = new java.util.HashMap<>();
        if (raw) {
            points.putAll(query.raw(dataItemIds, from, to));
        } else {
            if (!averaged.isEmpty()) {
                points.putAll(query.buckets(ids(averaged), from, to, anchor, bucket, true));
            }
            if (!last.isEmpty()) {
                points.putAll(query.buckets(ids(last), from, to, anchor, bucket, false));
            }
        }

        List<Interval> gaps = merge(query.disconnections(equipmentId, from, to), from, to);
        boolean connected = query.currentlyConnected(equipmentId);
        Map<Long, List<Alarm>> alarms = query.alarms(dataItemIds, from, to).stream()
                .collect(Collectors.groupingBy(Alarm::dataItemId));

        List<Series> series = new ArrayList<>();
        for (Long id : dataItemIds) {
            DataItemEntity item = items.get(id);
            List<Point> withGaps = new ArrayList<>(points.getOrDefault(id, List.of()));
            for (Interval gap : gaps) {
                withGaps.add(new Point(gap.startedAt(), null));
            }
            withGaps.sort(Comparator.comparing(Point::at).thenComparing(p -> p.value() == null ? 0 : 1));
            List<Alarm> ofItem = alarms.getOrDefault(id, List.of());
            List<Marker> markers = ofItem.stream()
                    .filter(a -> !a.occurredAt().isBefore(from) && !a.occurredAt().isAfter(to))
                    .map(a -> new Marker(a.alarmId(), a.occurredAt(), a.value(), a.grade())).toList();
            List<Abnormal> abnormal = ofItem.stream().map(a -> new Abnormal(
                    a.occurredAt().isBefore(from) ? from : a.occurredAt(),
                    a.closedAt() == null ? null : a.closedAt().isAfter(to) ? to : a.closedAt(), a.grade())).toList();
            boolean measurement = DataItemEntity.MEASUREMENT.equals(item.getItemKind());
            series.add(new Series(id, item.getName(), item.getItemKind(), item.getUnit(),
                    measurement ? item.getCautionLimit() : null, measurement ? item.getCriticalLimit() : null,
                    measurement ? item.getLimitDirection() : null, item.getDecimalPlaces(), item.getStateLabel0(),
                    item.getStateLabel1(), withGaps, markers, abnormal, raw ? item.getCollectCycleSec() : bucket));
        }
        return new Trend(equipmentId, series, gaps, connected, bucket, raw);
    }

    /** 기간별 시작 간격에서, 칸 수가 2000 이하가 될 때까지 다음 간격으로 올린다. */
    static int bucketSeconds(long rangeSeconds) {
        int base = rangeSeconds <= Duration.ofDays(7).getSeconds() ? 60 : 3600;
        for (int candidate : LADDER) {
            if (candidate >= base && Math.ceilDiv(rangeSeconds, candidate) <= MAX_POINTS) {
                return candidate;
            }
        }
        // 1일 칸으로도 2000칸을 넘으면(약 5.5년 이상) 2000칸이 되도록 늘린다.
        return (int) Math.ceilDiv(rangeSeconds, MAX_POINTS);
    }

    /** 칸 경계를 한국 시간 자정 기준으로 맞춘다. 1시간 칸은 정각, 1분 칸은 0초에서 시작한다. */
    static LocalDateTime anchor(LocalDateTime from, int bucket) {
        LocalDateTime midnight = from.toLocalDate().atStartOfDay();
        long offset = Duration.between(midnight, from).getSeconds();
        return midnight.plusSeconds(offset - Math.floorMod(offset, bucket));
    }

    /** 조회 구간으로 자르고 겹치는 구간을 합친다. 아직 끊겨 있는(endedAt=null) 구간은 null 로 둔다. */
    static List<Interval> merge(List<Interval> intervals, LocalDateTime from, LocalDateTime to) {
        List<Interval> clipped = intervals.stream()
                .map(i -> new Interval(i.startedAt().isBefore(from) ? from : i.startedAt(),
                        i.endedAt() == null ? null : i.endedAt().isAfter(to) ? to : i.endedAt()))
                .sorted(Comparator.comparing(Interval::startedAt)).toList();
        List<Interval> merged = new ArrayList<>();
        for (Interval gap : clipped) {
            if (!merged.isEmpty()) {
                Interval previous = merged.get(merged.size() - 1);
                if (previous.endedAt() == null || !gap.startedAt().isAfter(previous.endedAt())) {
                    LocalDateTime end = previous.endedAt() == null || gap.endedAt() == null ? null
                            : gap.endedAt().isAfter(previous.endedAt()) ? gap.endedAt() : previous.endedAt();
                    merged.set(merged.size() - 1, new Interval(previous.startedAt(), end));
                    continue;
                }
            }
            merged.add(gap);
        }
        return List.copyOf(merged);
    }

    private static List<Long> ids(List<DataItemEntity> items) {
        return items.stream().map(DataItemEntity::getId).toList();
    }
}

package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.UniqueViolations;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.entity.DataItemEntity;
import com.sfaasproject.stf.equipment.entity.DataItemEntity.Spec;
import com.sfaasproject.stf.equipment.entity.EquipmentEntity;
import com.sfaasproject.stf.equipment.repository.DataItemRepository;
import com.sfaasproject.stf.equipment.repository.EquipmentRepository;
import com.sfaasproject.stf.equipment.service.MasterDataItemView.AppliedThreshold;
import com.sfaasproject.stf.equipment.service.MasterDataItemView.StateAlarm;
import com.sfaasproject.stf.equipment.service.MasterDataItemView.Statistics;
import com.sfaasproject.stf.equipment.service.MasterDataItemView.Threshold;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MST-010~013 설비 데이터 항목과 알람 기준 (openapi v7 + 2026-11-18 사용자 결정).
 *
 * <ul>
 *   <li>측정값(MEASUREMENT): 주의·경보 기준과 방향. ABOVE 면 주의 &lt; 경보, BELOW 면 주의 &gt; 경보. 아니면 422
 *       INVALID_THRESHOLD_ORDER. 상태값 칸(0/1 표시, 상태 알람)은 받지 않는다. 소수 자릿수를 비우면 1.</li>
 *   <li>상태값(STATE): 0/1 표시 이름과 알람 발생 값(0 또는 1)·등급. 단위와 측정 기준은 받지 않는다. 소수 자릿수 0.</li>
 *   <li>누적값(CUMULATIVE): 알람 기준을 두지 않는다. 0/1 표시와 두 기준 모두 받지 않는다. 소수 자릿수 0.</li>
 *   <li>항목 코드(dataItemCode)는 수집과 맞추는 값이라 v7 에 추가했다. 설비 안에서 겹치면(삭제한 항목 포함)
 *       409 DATA_ITEM_CODE_DUPLICATE. 코드와 값 종류는 등록한 뒤 바꿀 수 없다(종류를 바꾸면 422
 *       DATA_ITEM_VALUE_TYPE_IMMUTABLE).</li>
 *   <li>통계 참고값은 최근 7일(오늘 포함, 한국 시간) 측정값의 최소·평균·최대이고 측정값 항목만 계산한다.
 *       알람 수는 지금부터 30일 전 이후에 발생한 알람이다.</li>
 *   <li>B 의 판정은 매번 DB 의 기준을 읽으므로 저장한 기준은 다음 판정부터 적용된다.</li>
 *   <li>길이는 ERD 컬럼에 맞춘다(이름 50, 단위 20, 0/1 표시 20, API_SPEC 11.0).</li>
 *   <li>MST-012 에서 두 기준 칸을 보내지 않으면 기존 기준을 유지하고, null 을 보내면 지운다(2026-11-18 사용자 결정).</li>
 * </ul>
 *
 * <p>바꾸는 작업은 설비 줄을 먼저 잠그고 항목 줄을 잠근다(설비 삭제 MST-004 와 같은 순서).
 */
@Service
public class MasterDataItemService {

    /** ERD data_item 컬럼 길이(API_SPEC 11.0). */
    static final int NAME_MAX = 50;
    static final int UNIT_MAX = 20;
    static final int LABEL_MAX = 20;
    static final int DEFAULT_MEASUREMENT_DECIMALS = 1;
    static final int RECENT_DAYS = 7;
    static final int ALARM_DAYS = 30;

    /**
     * 등록·수정 입력. dataItemCode 는 등록할 때만 쓴다.
     *
     * @param measurementThresholdGiven 요청에 measurementThreshold 칸이 있었는지. 수정에서 없으면 기존 값을 유지한다
     * @param stateAlarmConditionGiven 요청에 stateAlarmCondition 칸이 있었는지. 수정에서 없으면 기존 값을 유지한다
     */
    public record Command(String dataItemCode, String dataItemName, String valueType, String unit,
            Integer collectionIntervalSeconds, Integer decimalPlaces, String zeroDisplayName, String oneDisplayName,
            Threshold measurementThreshold, StateAlarm stateAlarmCondition,
            boolean measurementThresholdGiven, boolean stateAlarmConditionGiven) {

        /** 두 기준 칸을 모두 보낸 것으로 보는 입력(등록, 서비스 직접 호출). */
        public Command(String dataItemCode, String dataItemName, String valueType, String unit,
                Integer collectionIntervalSeconds, Integer decimalPlaces, String zeroDisplayName,
                String oneDisplayName, Threshold measurementThreshold, StateAlarm stateAlarmCondition) {
            this(dataItemCode, dataItemName, valueType, unit, collectionIntervalSeconds, decimalPlaces,
                    zeroDisplayName, oneDisplayName, measurementThreshold, stateAlarmCondition, true, true);
        }

        public record Threshold(Double cautionValue, Double criticalValue, String direction) { }

        public record StateAlarm(Integer alarmValue, String grade) { }
    }

    private record Locked(EquipmentEntity equipment, DataItemEntity item) { }

    private final DataItemRepository items;
    private final EquipmentRepository equipment;
    private final DataItemStatisticsQuery statistics;
    private final AuditRecorder audit;
    private final Clock clock;

    public MasterDataItemService(DataItemRepository items, EquipmentRepository equipment,
            DataItemStatisticsQuery statistics, AuditRecorder audit, Clock clock) {
        this.items = items;
        this.equipment = equipment;
        this.statistics = statistics;
        this.audit = audit;
        this.clock = clock;
    }

    /** MST-010. 없는 설비나 삭제한 설비는 빈 목록이다(v7 은 200/401 만 정의). */
    @Transactional(readOnly = true)
    public List<MasterDataItemView> list(long equipmentId) {
        boolean active = equipment.findById(equipmentId).filter(e -> !e.isDeleted()).isPresent();
        if (!active) {
            return List.of();
        }
        return views(items.findByEquipmentIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(equipmentId));
    }

    /** MST-011. */
    @Transactional
    public MasterDataItemView create(long equipmentId, Command command, long actorId) {
        String code = command.dataItemCode() == null ? "" : command.dataItemCode();
        if (code.isEmpty()) {
            throw validationError();
        }
        String kind = command.valueType();
        Spec spec = spec(kind, command);
        EquipmentEntity owner = lockEquipment(equipmentId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND",
                        "설비를 찾을 수 없습니다."));
        if (items.existsByEquipmentIdAndItemCode(equipmentId, code)) {
            throw duplicateCode();
        }
        DataItemEntity created;
        try {
            created = items.saveAndFlush(DataItemEntity.register(equipmentId, code, kind, spec,
                    items.maxSortOrder(equipmentId) + 1, now()));
        } catch (DataIntegrityViolationException raced) {
            // 같은 코드를 동시에 등록하면 UNIQUE 제약이 막는다. 길이 초과 같은 다른 DB 오류는 그대로 던진다.
            if (UniqueViolations.isUniqueViolation(raced)) {
                throw duplicateCode();
            }
            throw raced;
        }
        audit.record(actorId, AuditTargetType.DATA_ITEM, target(owner, created), "항목 등록", null, created.getName());
        return view(created);
    }

    /**
     * MST-012. 요청 내용으로 항목을 바꾼다. 기준 칸을 보내지 않으면 기존 기준을 유지하고 null 을 보내면 지운다.
     * 바뀐 칸만 변경 이력에 남긴다.
     */
    @Transactional
    public MasterDataItemView update(long dataItemId, Command command, long actorId) {
        Locked locked = lock(dataItemId);
        DataItemEntity item = locked.item();
        if (command.valueType() != null && !command.valueType().equals(item.getItemKind())) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "DATA_ITEM_VALUE_TYPE_IMMUTABLE",
                    "데이터 항목의 값 종류는 바꿀 수 없습니다.");
        }
        Spec before = item.spec();
        Spec next = spec(item.getItemKind(), keepOmittedThresholds(command, before));
        String target = target(locked.equipment(), item);
        record(actorId, AuditTargetType.DATA_ITEM, target, "이름", before.name(), next.name());
        record(actorId, AuditTargetType.DATA_ITEM, target, "단위", before.unit(), next.unit());
        record(actorId, AuditTargetType.DATA_ITEM, target, "수집 주기", before.collectCycleSec(), next.collectCycleSec());
        record(actorId, AuditTargetType.DATA_ITEM, target, "소수 자릿수", before.decimalPlaces(), next.decimalPlaces());
        record(actorId, AuditTargetType.DATA_ITEM, target, "0 표시", before.stateLabel0(), next.stateLabel0());
        record(actorId, AuditTargetType.DATA_ITEM, target, "1 표시", before.stateLabel1(), next.stateLabel1());
        recordThreshold(actorId, target, before, next);
        item.apply(next, now());
        items.flush();
        return view(item);
    }

    /** MST-013. 측정값은 주의·경보 기준과 방향, 상태값은 알람 발생 값과 등급을 바꾼다. 누적값은 422. */
    @Transactional
    public AppliedThreshold updateThreshold(long dataItemId, Command.Threshold threshold,
            Command.StateAlarm stateAlarm, long actorId) {
        Locked locked = lock(dataItemId);
        DataItemEntity item = locked.item();
        Spec before = item.spec();
        Spec next;
        switch (item.getItemKind()) {
            case DataItemEntity.MEASUREMENT -> {
                if (stateAlarm != null || threshold == null) {
                    throw validationError();
                }
                Threshold value = measurementThreshold(threshold);
                next = new Spec(before.name(), before.unit(), before.collectCycleSec(), before.decimalPlaces(),
                        value.cautionValue(), value.criticalValue(), value.direction(),
                        before.stateLabel0(), before.stateLabel1(), null, null);
            }
            case DataItemEntity.STATE -> {
                if (threshold != null || stateAlarm == null) {
                    throw validationError();
                }
                StateAlarm value = stateAlarm(stateAlarm);
                next = new Spec(before.name(), before.unit(), before.collectCycleSec(), before.decimalPlaces(),
                        null, null, null, before.stateLabel0(), before.stateLabel1(), value.alarmValue(),
                        value.grade());
            }
            default -> throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "THRESHOLD_NOT_SUPPORTED",
                    "누적값 항목에는 알람 기준을 둘 수 없습니다.");
        }
        recordThreshold(actorId, target(locked.equipment(), item), before, next);
        LocalDateTime now = now();
        item.apply(next, now);
        items.flush();
        return new AppliedThreshold(item.getId(), threshold(item), stateAlarm(item), now);
    }

    /** 수정 요청에 없던 기준 칸을 지금 저장된 기준으로 채운다. */
    private static Command keepOmittedThresholds(Command command, Spec before) {
        Command.Threshold threshold = command.measurementThresholdGiven() ? command.measurementThreshold()
                : before.limitDirection() == null ? null
                : new Command.Threshold(before.cautionLimit(), before.criticalLimit(), before.limitDirection());
        Command.StateAlarm stateAlarm = command.stateAlarmConditionGiven() ? command.stateAlarmCondition()
                : before.alarmStateValue() == null ? null
                : new Command.StateAlarm(before.alarmStateValue(), before.alarmStateGrade());
        return new Command(command.dataItemCode(), command.dataItemName(), command.valueType(), command.unit(),
                command.collectionIntervalSeconds(), command.decimalPlaces(), command.zeroDisplayName(),
                command.oneDisplayName(), threshold, stateAlarm, true, true);
    }

    // ---------- 검증 ----------

    private static Spec spec(String kind, Command command) {
        String name = required(command.dataItemName(), NAME_MAX);
        String unit = optional(command.unit(), UNIT_MAX);
        Integer cycle = command.collectionIntervalSeconds();
        if (cycle == null || cycle < 1) {
            throw validationError();
        }
        Integer decimals = command.decimalPlaces();
        if (decimals != null && (decimals < 0 || decimals > 10)) {
            throw validationError();
        }
        String zero = optional(command.zeroDisplayName(), LABEL_MAX);
        String one = optional(command.oneDisplayName(), LABEL_MAX);
        if (kind == null) {
            throw validationError();
        }
        switch (kind) {
            case DataItemEntity.MEASUREMENT -> {
                if (zero != null || one != null || command.stateAlarmCondition() != null) {
                    throw validationError();
                }
                Threshold t = command.measurementThreshold() == null ? null
                        : measurementThreshold(command.measurementThreshold());
                return new Spec(name, unit, cycle, decimals == null ? DEFAULT_MEASUREMENT_DECIMALS : decimals,
                        t == null ? null : t.cautionValue(), t == null ? null : t.criticalValue(),
                        t == null ? null : t.direction(), null, null, null, null);
            }
            case DataItemEntity.STATE -> {
                if (unit != null || command.measurementThreshold() != null || (decimals != null && decimals != 0)) {
                    throw validationError();
                }
                StateAlarm s = command.stateAlarmCondition() == null ? null
                        : stateAlarm(command.stateAlarmCondition());
                return new Spec(name, null, cycle, 0, null, null, null, zero, one,
                        s == null ? null : s.alarmValue(), s == null ? null : s.grade());
            }
            case DataItemEntity.CUMULATIVE -> {
                if (zero != null || one != null || command.measurementThreshold() != null
                        || command.stateAlarmCondition() != null || (decimals != null && decimals != 0)) {
                    throw validationError();
                }
                return new Spec(name, unit, cycle, 0, null, null, null, null, null, null, null);
            }
            default -> throw validationError();
        }
    }

    private static Threshold measurementThreshold(Command.Threshold input) {
        Double caution = input.cautionValue();
        Double critical = input.criticalValue();
        String direction = input.direction();
        if (caution == null || critical == null || !Double.isFinite(caution) || !Double.isFinite(critical)
                || !("ABOVE".equals(direction) || "BELOW".equals(direction))) {
            throw validationError();
        }
        boolean ordered = "ABOVE".equals(direction) ? caution < critical : caution > critical;
        if (!ordered) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_THRESHOLD_ORDER",
                    "주의·경보 기준 순서를 확인해 주세요.");
        }
        return new Threshold(caution, critical, direction);
    }

    private static StateAlarm stateAlarm(Command.StateAlarm input) {
        Integer value = input.alarmValue();
        String grade = input.grade();
        if (value == null || (value != 0 && value != 1)
                || !("CAUTION".equals(grade) || "CRITICAL".equals(grade))) {
            throw validationError();
        }
        return new StateAlarm(value, grade);
    }

    private static String required(String value, int max) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty() || clean.length() > max) {
            throw validationError();
        }
        return clean;
    }

    private static String optional(String value, int max) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() > max) {
            throw validationError();
        }
        return clean.isEmpty() ? null : clean;
    }

    // ---------- 잠금 ----------

    private java.util.Optional<EquipmentEntity> lockEquipment(long equipmentId) {
        return equipment.lockAllById(List.of(equipmentId)).stream().filter(e -> !e.isDeleted()).findFirst();
    }

    private Locked lock(long dataItemId) {
        long equipmentId = items.findActiveEquipmentId(dataItemId).orElseThrow(MasterDataItemService::itemNotFound);
        EquipmentEntity owner = lockEquipment(equipmentId).orElseThrow(MasterDataItemService::itemNotFound);
        DataItemEntity item = items.findActiveForUpdate(dataItemId).orElseThrow(MasterDataItemService::itemNotFound);
        return new Locked(owner, item);
    }

    // ---------- 응답 ----------

    private MasterDataItemView view(DataItemEntity item) {
        return views(List.of(item)).get(0);
    }

    private List<MasterDataItemView> views(List<DataItemEntity> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = now();
        List<Long> ids = source.stream().map(DataItemEntity::getId).toList();
        List<Long> measurementIds = source.stream()
                .filter(item -> DataItemEntity.MEASUREMENT.equals(item.getItemKind()))
                .map(DataItemEntity::getId).toList();
        Map<Long, DataItemStatisticsQuery.Latest> latest = statistics.latest(ids);
        Map<Long, DataItemStatisticsQuery.Range> ranges = statistics.ranges(measurementIds,
                now.toLocalDate().minusDays(RECENT_DAYS - 1).atStartOfDay());
        Map<Long, Integer> alarms = statistics.alarmCounts(ids, now.minusDays(ALARM_DAYS));
        return source.stream().map(item -> {
            DataItemStatisticsQuery.Latest last = latest.get(item.getId());
            DataItemStatisticsQuery.Range range = ranges.get(item.getId());
            Statistics stats = new Statistics(range == null ? null : range.minimum(),
                    range == null ? null : range.average(), range == null ? null : range.maximum(),
                    alarms.getOrDefault(item.getId(), 0));
            return new MasterDataItemView(item.getId(), item.getItemCode(), item.getName(), item.getItemKind(),
                    item.getUnit(), item.getCollectCycleSec(), item.getDecimalPlaces(), item.getStateLabel0(),
                    item.getStateLabel1(), last == null ? null : last.value(),
                    last == null ? null : last.receivedAt(), threshold(item), stateAlarm(item), stats);
        }).toList();
    }

    private static Threshold threshold(DataItemEntity item) {
        if (item.getCautionLimit() == null || item.getCriticalLimit() == null || item.getLimitDirection() == null) {
            return null;
        }
        return new Threshold(item.getCautionLimit(), item.getCriticalLimit(), item.getLimitDirection());
    }

    private static StateAlarm stateAlarm(DataItemEntity item) {
        if (item.getAlarmStateValue() == null || item.getAlarmStateGrade() == null) {
            return null;
        }
        return new StateAlarm(item.getAlarmStateValue(), item.getAlarmStateGrade());
    }

    // ---------- 변경 이력 ----------

    private void recordThreshold(long actorId, String target, Spec before, Spec next) {
        record(actorId, AuditTargetType.THRESHOLD, target, "주의 기준", before.cautionLimit(), next.cautionLimit());
        record(actorId, AuditTargetType.THRESHOLD, target, "경보 기준", before.criticalLimit(), next.criticalLimit());
        record(actorId, AuditTargetType.THRESHOLD, target, "기준 방향", before.limitDirection(), next.limitDirection());
        record(actorId, AuditTargetType.THRESHOLD, target, "알람 발생 값", before.alarmStateValue(),
                next.alarmStateValue());
        record(actorId, AuditTargetType.THRESHOLD, target, "알람 등급", before.alarmStateGrade(),
                next.alarmStateGrade());
    }

    private void record(long actorId, AuditTargetType type, String target, String field, Object before,
            Object after) {
        if (!Objects.equals(before, after)) {
            audit.record(actorId, type, target, field, text(before), text(after));
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double number && number == Math.rint(number) && Math.abs(number) < 1e15) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(value);
    }

    private static String target(EquipmentEntity owner, DataItemEntity item) {
        return owner.getEquipCode() + "/" + item.getItemCode();
    }

    private LocalDateTime now() {
        return KstTime.toLocal(clock.instant());
    }

    private static DomainException itemNotFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "DATA_ITEM_NOT_FOUND", "데이터 항목을 찾을 수 없습니다.");
    }

    private static DomainException duplicateCode() {
        return new DomainException(HttpStatus.CONFLICT, "DATA_ITEM_CODE_DUPLICATE", "이미 사용 중인 항목 코드입니다.");
    }

    private static DomainException validationError() {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
    }
}

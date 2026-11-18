package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.equipment.entity.DataItemEntity;
import com.sfaasproject.stf.equipment.entity.EquipmentEntity;
import com.sfaasproject.stf.equipment.entity.LineEntity;
import com.sfaasproject.stf.equipment.entity.ProcessEntity;
import com.sfaasproject.stf.equipment.repository.DataItemRepository;
import com.sfaasproject.stf.equipment.repository.EquipmentRepository;
import com.sfaasproject.stf.equipment.repository.LineRepository;
import com.sfaasproject.stf.equipment.repository.ProcessRepository;
import com.sfaasproject.stf.equipment.service.EquipmentQueryView.Abnormal;
import com.sfaasproject.stf.equipment.service.EquipmentQueryView.CurrentItem;
import com.sfaasproject.stf.equipment.service.EquipmentQueryView.Detail;
import com.sfaasproject.stf.equipment.service.EquipmentQueryView.Summary;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EQP-002~005 설비 현황 조회 (openapi v7). 판정 규칙은 B 의 종합현황(OVW-001, V7OverviewService)과 같게 맞춘다.
 *
 * <ul>
 *   <li>상태 줄(equipment_state)이 없는 설비는 COMMUNICATION_LOST, 시작 시각은 설비 등록 시각.</li>
 *   <li>이상 설비는 FAULT·CAUTION·COMMUNICATION_LOST.</li>
 *   <li>측정값 valueStatus 는 지금 값을 기준과 비교한다(경보 먼저, 기준 포함). 상태값은 알람 발생 값과 같으면
 *       그 등급, 누적값은 null. 기준이 없으면 NORMAL.</li>
 *   <li>CurrentDataItem 은 value·measuredAt 이 필수라 값을 받은 적 없는 항목은 목록에서 뺀다.</li>
 *   <li>주요 측정값(mainMeasurements)은 표시 순서가 앞선 측정값 3개다(OVW-001 과 같음).</li>
 * </ul>
 */
@Service
public class EquipmentQueryService {

    static final String COMMUNICATION_LOST = "COMMUNICATION_LOST";
    static final int MAIN_MEASUREMENTS = 3;
    /** 대표 상태를 고를 때 나쁜 순서(API_SPEC 4.1 설비 상태 우선순위). */
    static final List<String> STATUS_PRIORITY = List.of("COMMUNICATION_LOST", "FAULT", "CAUTION", "IDLE", "RUNNING");

    private final LineRepository lines;
    private final ProcessRepository processes;
    private final EquipmentRepository equipment;
    private final DataItemRepository dataItems;
    private final EquipmentRuntimeQuery runtime;
    private final DataItemStatisticsQuery values;
    private final CurrentDataItemDecisionService currentItems;
    private final MasterEquipmentService masterEquipment;

    public EquipmentQueryService(LineRepository lines, ProcessRepository processes, EquipmentRepository equipment,
            DataItemRepository dataItems, EquipmentRuntimeQuery runtime, DataItemStatisticsQuery values,
            CurrentDataItemDecisionService currentItems, MasterEquipmentService masterEquipment) {
        this.lines = lines;
        this.processes = processes;
        this.equipment = equipment;
        this.dataItems = dataItems;
        this.runtime = runtime;
        this.values = values;
        this.currentItems = currentItems;
        this.masterEquipment = masterEquipment;
    }

    /** EQP-002. 사용 중인 라인의 공정을 공정 순서대로. 라인이 없거나 삭제됐으면 404 LINE_NOT_FOUND. */
    @Transactional(readOnly = true)
    public List<EquipmentQueryView.Process> processes(long lineId) {
        lines.findByIdAndDeletedAtIsNull(lineId).orElseThrow(() -> notFound("LINE_NOT_FOUND", "라인을 찾을 수 없습니다."));
        List<ProcessEntity> found = processes.findByLineIdOrderByProcessSeqAscIdAsc(lineId);
        List<EquipmentEntity> placed = found.isEmpty() ? List.of()
                : equipment.findByProcessIdInAndDeletedAtIsNull(found.stream().map(ProcessEntity::getId).toList());
        Map<Long, EquipmentRuntimeQuery.Runtime> states = runtime.find(placed.stream().map(EquipmentEntity::getId)
                .toList());
        Map<Long, List<String>> statusByProcess = placed.stream().collect(Collectors.groupingBy(
                EquipmentEntity::getProcessId, Collectors.mapping(item -> status(states.get(item.getId())),
                        Collectors.toList())));
        return found.stream().map(process -> {
            List<String> statuses = statusByProcess.getOrDefault(process.getId(), List.of());
            String worst = statuses.stream().min(Comparator.comparingInt(STATUS_PRIORITY::indexOf)).orElse(null);
            return new EquipmentQueryView.Process(process.getId(), process.getName(), process.getProcessSeq(),
                    statuses.stream().anyMatch(EquipmentQueryService::isAbnormal), worst);
        }).toList();
    }

    /** EQP-003. 공정의 사용 중인 설비를 번호순으로. 공정이 없거나 라인이 삭제됐으면 404 PROCESS_NOT_FOUND. */
    @Transactional(readOnly = true)
    public List<Summary> processEquipment(long processId) {
        ProcessEntity process = processes.findById(processId)
                .orElseThrow(EquipmentQueryService::processNotFound);
        LineEntity line = lines.findByIdAndDeletedAtIsNull(process.getLineId())
                .orElseThrow(EquipmentQueryService::processNotFound);
        List<EquipmentEntity> placed = new ArrayList<>(
                equipment.findByProcessIdInAndDeletedAtIsNull(List.of(processId)));
        placed.sort(Comparator.comparingLong(EquipmentEntity::getId));
        List<Long> ids = placed.stream().map(EquipmentEntity::getId).toList();
        Map<Long, EquipmentRuntimeQuery.Runtime> states = runtime.find(ids);
        Map<Long, List<DataItemEntity>> itemsByEquipment = (ids.isEmpty() ? List.<DataItemEntity>of()
                : dataItems.findByEquipmentIdInAndDeletedAtIsNullOrderBySortOrderAscIdAsc(ids)).stream()
                .collect(Collectors.groupingBy(DataItemEntity::getEquipmentId));
        Map<Long, DataItemStatisticsQuery.Latest> latest = values.latest(itemsByEquipment.values().stream()
                .flatMap(List::stream).map(DataItemEntity::getId).toList());
        return placed.stream().map(item -> {
            EquipmentRuntimeQuery.Runtime state = states.get(item.getId());
            List<DataItemEntity> items = itemsByEquipment.getOrDefault(item.getId(), List.of());
            List<CurrentItem> measured = current(items, latest).stream()
                    .filter(c -> DataItemEntity.MEASUREMENT.equals(c.valueType())).toList();
            return new Summary(item.getId(), item.getEquipCode(), item.getName(), line.getId(), line.getName(),
                    line.getLineType(), process.getId(), process.getName(), process.getProcessSeq(), status(state),
                    statusSince(item, state), state == null ? 0 : state.activeAlarmCount(),
                    measured.stream().limit(MAIN_MEASUREMENTS).toList(), abnormal(items, latest));
        }).toList();
    }

    /** EQP-004. 사용 중인 설비가 아니면 404 EQUIPMENT_NOT_FOUND. */
    @Transactional(readOnly = true)
    public Detail detail(long equipmentId) {
        MasterEquipmentView basic = masterEquipment.findActive(equipmentId)
                .orElseThrow(EquipmentQueryService::equipmentNotFound);
        EquipmentEntity item = equipment.findById(equipmentId).orElseThrow(EquipmentQueryService::equipmentNotFound);
        EquipmentRuntimeQuery.Runtime state = runtime.find(List.of(equipmentId)).get(equipmentId);
        List<CurrentItem> all = currentItems(equipmentId);
        return new Detail(basic, status(state), statusSince(item, state), state == null ? null : state.lastReceivedAt(),
                byType(all, DataItemEntity.MEASUREMENT), byType(all, DataItemEntity.STATE),
                byType(all, DataItemEntity.CUMULATIVE));
    }

    /** EQP-005. 사용 중인 설비가 아니면 404 EQUIPMENT_NOT_FOUND. */
    @Transactional(readOnly = true)
    public List<CurrentItem> dataItems(long equipmentId) {
        masterEquipment.findActive(equipmentId).orElseThrow(EquipmentQueryService::equipmentNotFound);
        return currentItems(equipmentId);
    }

    // ---------- 판정 ----------

    private List<CurrentItem> currentItems(long equipmentId) {
        List<DataItemEntity> items = dataItems.findByEquipmentIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
                equipmentId);
        return current(items, values.latest(items.stream().map(DataItemEntity::getId).toList()));
    }

    private List<CurrentItem> current(List<DataItemEntity> items, Map<Long, DataItemStatisticsQuery.Latest> latest) {
        List<CurrentItem> result = new ArrayList<>();
        for (DataItemEntity item : items) {
            DataItemStatisticsQuery.Latest last = latest.get(item.getId());
            if (last == null || last.measuredAt() == null) {
                continue;
            }
            BigDecimal value = BigDecimal.valueOf(last.value());
            CurrentDataItemDecisionService.CurrentItem mapped;
            try {
                mapped = currentItems.map(decisionItem(item), value, grade(item, value), last.measuredAt());
            } catch (IllegalArgumentException | ArithmeticException badValue) {
                // 수집이 막지 못한 형식(상태값 0/1 이 아님, 누적값 소수 등)은 화면을 깨지 않도록 건너뛴다.
                continue;
            }
            result.add(new CurrentItem(mapped.dataItemId(), mapped.dataItemName(), mapped.valueType(), mapped.value(),
                    mapped.displayValue(), mapped.unit(), mapped.valueStatus(), mapped.cautionThreshold(),
                    mapped.criticalThreshold(), mapped.measuredAt()));
        }
        return result;
    }

    private List<Abnormal> abnormal(List<DataItemEntity> items, Map<Long, DataItemStatisticsQuery.Latest> latest) {
        List<Abnormal> result = new ArrayList<>();
        for (CurrentItem current : current(items, latest)) {
            if (!"CAUTION".equals(current.valueStatus()) && !"CRITICAL".equals(current.valueStatus())) {
                continue;
            }
            DataItemEntity item = items.stream().filter(i -> i.getId() == current.dataItemId()).findFirst()
                    .orElseThrow();
            BigDecimal threshold = DataItemEntity.STATE.equals(item.getItemKind())
                    ? BigDecimal.valueOf(item.getAlarmStateValue())
                    : "CRITICAL".equals(current.valueStatus()) ? current.criticalThreshold()
                    : current.cautionThreshold();
            result.add(new Abnormal(current.dataItemId(), current.dataItemName(), current.value(),
                    current.displayValue(), current.unit(), current.valueStatus(), threshold,
                    current.cautionThreshold(), current.criticalThreshold(), item.getLimitDirection(),
                    item.getDecimalPlaces()));
        }
        return result;
    }

    /** B 의 OVW-001 과 같은 값 판정. 누적값은 null(CurrentDataItemDecisionService 가 비운다). */
    static String grade(DataItemEntity item, BigDecimal value) {
        if (DataItemEntity.MEASUREMENT.equals(item.getItemKind())) {
            if (item.getCautionLimit() == null || item.getCriticalLimit() == null || item.getLimitDirection() == null) {
                return "NORMAL";
            }
            boolean above = "ABOVE".equals(item.getLimitDirection());
            BigDecimal critical = BigDecimal.valueOf(item.getCriticalLimit());
            BigDecimal caution = BigDecimal.valueOf(item.getCautionLimit());
            if (above ? value.compareTo(critical) >= 0 : value.compareTo(critical) <= 0) {
                return "CRITICAL";
            }
            if (above ? value.compareTo(caution) >= 0 : value.compareTo(caution) <= 0) {
                return "CAUTION";
            }
            return "NORMAL";
        }
        if (DataItemEntity.STATE.equals(item.getItemKind())) {
            if (item.getAlarmStateValue() != null && item.getAlarmStateGrade() != null
                    && value.intValue() == item.getAlarmStateValue()) {
                return item.getAlarmStateGrade();
            }
            return "NORMAL";
        }
        return null;
    }

    private static CurrentDataItemDecisionService.Item decisionItem(DataItemEntity item) {
        return new CurrentDataItemDecisionService.Item(item.getId(), item.getName(), item.getItemKind(), item.getUnit(),
                item.getDecimalPlaces(),
                item.getCautionLimit() == null ? null : BigDecimal.valueOf(item.getCautionLimit()),
                item.getCriticalLimit() == null ? null : BigDecimal.valueOf(item.getCriticalLimit()),
                item.getStateLabel0(), item.getStateLabel1());
    }

    private static List<CurrentItem> byType(List<CurrentItem> items, String type) {
        return items.stream().filter(item -> type.equals(item.valueType())).toList();
    }

    private static String status(EquipmentRuntimeQuery.Runtime state) {
        return state == null || state.status() == null ? COMMUNICATION_LOST : state.status();
    }

    private static LocalDateTime statusSince(EquipmentEntity item, EquipmentRuntimeQuery.Runtime state) {
        return state == null || state.statusSince() == null ? item.getCreatedAt() : state.statusSince();
    }

    private static boolean isAbnormal(String status) {
        return "FAULT".equals(status) || "CAUTION".equals(status) || COMMUNICATION_LOST.equals(status);
    }

    private static DomainException notFound(String code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }

    private static DomainException processNotFound() {
        return notFound("PROCESS_NOT_FOUND", "공정을 찾을 수 없습니다.");
    }

    private static DomainException equipmentNotFound() {
        return notFound("EQUIPMENT_NOT_FOUND", "설비를 찾을 수 없습니다.");
    }
}

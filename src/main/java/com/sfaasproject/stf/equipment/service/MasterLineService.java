package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.entity.EquipmentEntity;
import com.sfaasproject.stf.equipment.entity.LineEntity;
import com.sfaasproject.stf.equipment.entity.ProcessEntity;
import com.sfaasproject.stf.equipment.repository.EquipmentRepository;
import com.sfaasproject.stf.equipment.repository.LineRepository;
import com.sfaasproject.stf.equipment.repository.ProcessRepository;
import com.sfaasproject.stf.equipment.service.MasterLineView.EquipmentSummaryView;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MST-005~009 라인과 설비 배치 기준정보 (openapi v7). 바꾸는 작업은 라인 줄을 잠그고 처리하며 변경 이력을 같은 트랜잭션에 남긴다.
 *
 * <ul>
 *   <li>lineType 을 등록에서 생략하면 PART, 수정에서 생략하면 기존 값을 유지한다(v7 LineWriteRequest).</li>
 *   <li>라인 이름은 v7 대로 100자까지 받는다. DB line.name 은 varchar(50) UNIQUE 다.
 *       삭제한 라인을 포함해 이름이 겹치면 LINE_NAME_DUPLICATE(409). v7 에 정의가 없어 기존 코드를 유지한다.</li>
 *   <li>배치 변경은 이 라인의 설비만, 이 라인에 이미 있는 공정 순서로만 옮긴다. 아니면 v7 MST-009 의 400.</li>
 * </ul>
 */
@Service
public class MasterLineService {

    /** v7 LineWriteRequest.lineName 최대 길이. */
    static final int NAME_MAX = 100;
    /** equipment_state 줄이 없는 설비는 종합현황(OVW-001)과 같이 통신 끊김으로 보여 준다. */
    static final String STATUS_WITHOUT_STATE = "COMMUNICATION_LOST";

    public record LayoutItem(Long equipmentId, Integer processOrder) { }

    private final LineRepository lines;
    private final ProcessRepository processes;
    private final EquipmentRepository equipment;
    private final EquipmentRuntimeQuery runtime;
    private final AuditRecorder audit;
    private final Clock clock;

    public MasterLineService(LineRepository lines, ProcessRepository processes, EquipmentRepository equipment,
            EquipmentRuntimeQuery runtime, AuditRecorder audit, Clock clock) {
        this.lines = lines;
        this.processes = processes;
        this.equipment = equipment;
        this.runtime = runtime;
        this.audit = audit;
        this.clock = clock;
    }

    /** MST-005. 사용 중인 라인을 표시순서대로, 각 라인의 설비를 공정 순서대로 돌려준다. */
    @Transactional(readOnly = true)
    public List<MasterLineView> list() {
        return views(lines.findByDeletedAtIsNullOrderBySortOrderAscIdAsc());
    }

    /** MST-006. */
    @Transactional
    public MasterLineView create(String lineName, Integer displayOrder, String lineType, long actorId) {
        String name = cleanName(lineName);
        int order = cleanOrder(displayOrder);
        String type = cleanType(lineType, LineEntity.TYPE_PART);
        if (lines.existsByName(name)) {
            throw duplicateName();
        }
        LineEntity line;
        try {
            line = lines.saveAndFlush(LineEntity.create(name, type, order, now()));
        } catch (DataIntegrityViolationException raced) {
            // 같은 이름을 동시에 등록하면 UNIQUE 제약이 막는다. 이름 길이 초과 같은 다른 DB 오류는 그대로 던진다.
            if (lines.existsByName(name)) {
                throw duplicateName();
            }
            throw raced;
        }
        audit.record(actorId, AuditTargetType.LINE, name, "라인 등록", null, name);
        return view(line);
    }

    /** MST-007. 바뀐 항목만 변경 이력에 남긴다. */
    @Transactional
    public MasterLineView update(long lineId, String lineName, Integer displayOrder, String lineType,
            long actorId) {
        String name = cleanName(lineName);
        int order = cleanOrder(displayOrder);
        LineEntity line = lock(lineId);
        String oldName = line.getName();
        int oldOrder = line.getSortOrder();
        String oldType = line.getLineType();
        String type = cleanType(lineType, oldType);
        boolean nameChanged = !oldName.equals(name);
        boolean orderChanged = oldOrder != order;
        boolean typeChanged = !oldType.equals(type);
        if (nameChanged && lines.existsByNameAndIdNot(name, lineId)) {
            throw duplicateName();
        }
        if (nameChanged || orderChanged || typeChanged) {
            line.update(name, type, order, now());
            try {
                lines.flush();
            } catch (DataIntegrityViolationException raced) {
                if (lines.existsByNameAndIdNot(name, lineId)) {
                    throw duplicateName();
                }
                throw raced;
            }
            if (nameChanged) {
                audit.record(actorId, AuditTargetType.LINE, oldName, "이름", oldName, name);
            }
            if (orderChanged) {
                audit.record(actorId, AuditTargetType.LINE, name, "표시순서",
                        String.valueOf(oldOrder), String.valueOf(order));
            }
            if (typeChanged) {
                audit.record(actorId, AuditTargetType.LINE, name, "라인 종류", oldType, type);
            }
        }
        return view(line);
    }

    /**
     * MST-008. 사용 중인 설비가 없는 라인만 논리 삭제한다. line 에는 삭제한 사람 컬럼이 없으므로
     * 삭제한 사람은 변경 이력(change_log)에 남긴다. 공정 줄은 이력 보존을 위해 그대로 둔다.
     */
    @Transactional
    public void delete(long lineId, long actorId) {
        LineEntity line = lock(lineId);
        if (equipment.existsActiveInLine(lineId)) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "LINE_HAS_EQUIPMENT",
                    "설비가 있는 라인은 삭제할 수 없습니다.");
        }
        line.markDeleted(now());
        audit.record(actorId, AuditTargetType.LINE, line.getName(), "라인 삭제", "사용", "삭제");
    }

    /**
     * MST-009. 요청한 설비들을 한 번에 다른 공정 순서로 옮긴다. 하나라도 맞지 않으면 아무것도 바꾸지 않는다.
     * 요청에 없는 설비는 그대로 둔다.
     */
    @Transactional
    public MasterLineView updateLayout(long lineId, List<LayoutItem> items, long actorId) {
        if (items == null || items.isEmpty()) {
            throw validationError();
        }
        Map<Long, Integer> targetOrder = new LinkedHashMap<>();
        for (LayoutItem item : items) {
            if (item == null || item.equipmentId() == null || item.processOrder() == null
                    || item.processOrder() < 1 || targetOrder.put(item.equipmentId(), item.processOrder()) != null) {
                throw validationError();
            }
        }
        LineEntity line = lock(lineId);
        Map<Integer, ProcessEntity> bySeq = new HashMap<>();
        Map<Long, ProcessEntity> byId = new HashMap<>();
        for (ProcessEntity process : processes.findByLineIdOrderByProcessSeqAscIdAsc(lineId)) {
            bySeq.putIfAbsent(process.getProcessSeq(), process);
            byId.put(process.getId(), process);
        }

        List<EquipmentEntity> targets = equipment.lockAllById(targetOrder.keySet());
        Set<Long> found = targets.stream().filter(e -> !e.isDeleted()).map(EquipmentEntity::getId)
                .collect(Collectors.toSet());
        if (!found.containsAll(targetOrder.keySet())) {
            throw new DomainException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "설비를 찾을 수 없습니다.");
        }
        for (EquipmentEntity target : targets) {
            if (!byId.containsKey(target.getProcessId())) {
                // 다른 라인의 설비. 라인 이동은 설비 수정(MST-003)에서 한다.
                throw validationError();
            }
        }
        for (Integer order : targetOrder.values()) {
            if (!bySeq.containsKey(order)) {
                // 이 라인에 없는 공정 순서. v7 MST-009 는 400/404 만 정의한다.
                throw validationError();
            }
        }

        LocalDateTime now = now();
        for (EquipmentEntity target : targets) {
            ProcessEntity from = byId.get(target.getProcessId());
            ProcessEntity to = bySeq.get(targetOrder.get(target.getId()));
            if (!from.getId().equals(to.getId())) {
                target.moveToProcess(to.getId(), now);
                audit.record(actorId, AuditTargetType.EQUIPMENT, target.getEquipCode(), "공정 배치",
                        processLabel(from), processLabel(to));
            }
        }
        equipment.flush();
        return view(line);
    }

    private MasterLineView view(LineEntity line) {
        return views(List.of(line)).get(0);
    }

    private List<MasterLineView> views(List<LineEntity> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<Long> lineIds = source.stream().map(LineEntity::getId).toList();
        Map<Long, ProcessEntity> processById = processes.findByLineIdInOrderByLineIdAscProcessSeqAscIdAsc(lineIds)
                .stream().collect(Collectors.toMap(ProcessEntity::getId, Function.identity()));
        List<EquipmentEntity> placed = processById.isEmpty() ? List.of()
                : equipment.findByProcessIdInAndDeletedAtIsNull(processById.keySet());
        Map<Long, EquipmentRuntimeQuery.Runtime> states =
                runtime.find(placed.stream().map(EquipmentEntity::getId).toList());

        Map<Long, List<EquipmentSummaryView>> byLine = new HashMap<>();
        Map<Long, LineEntity> lineById = source.stream()
                .collect(Collectors.toMap(LineEntity::getId, Function.identity()));
        for (EquipmentEntity item : placed) {
            ProcessEntity process = processById.get(item.getProcessId());
            LineEntity line = lineById.get(process.getLineId());
            EquipmentRuntimeQuery.Runtime state = states.get(item.getId());
            String status = state == null || state.status() == null ? STATUS_WITHOUT_STATE : state.status();
            LocalDateTime since = state == null || state.statusSince() == null ? item.getCreatedAt()
                    : state.statusSince();
            byLine.computeIfAbsent(line.getId(), id -> new ArrayList<>()).add(new EquipmentSummaryView(
                    item.getId(), item.getEquipCode(), item.getName(), line.getId(), line.getName(),
                    process.getId(), process.getName(), process.getProcessSeq(), status, since,
                    state == null ? 0 : state.activeAlarmCount(), line.getLineType()));
        }
        Comparator<EquipmentSummaryView> order = Comparator.comparingInt(EquipmentSummaryView::processOrder)
                .thenComparingLong(EquipmentSummaryView::equipmentId);
        List<MasterLineView> result = new ArrayList<>();
        for (LineEntity line : source) {
            List<EquipmentSummaryView> items = new ArrayList<>(byLine.getOrDefault(line.getId(), List.of()));
            items.sort(order);
            result.add(new MasterLineView(line.getId(), line.getName(), line.getSortOrder(), line.getLineType(),
                    List.copyOf(items)));
        }
        return result;
    }

    private LineEntity lock(long lineId) {
        return lines.findActiveForUpdate(lineId).orElseThrow(MasterLineService::lineNotFound);
    }

    private static String cleanName(String lineName) {
        String name = lineName == null ? "" : lineName.strip();
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw validationError();
        }
        return name;
    }

    private static String cleanType(String lineType, String fallback) {
        if (lineType == null) {
            return fallback;
        }
        if (!LineEntity.TYPE_PART.equals(lineType) && !LineEntity.TYPE_ASSEMBLY.equals(lineType)) {
            throw validationError();
        }
        return lineType;
    }

    private static int cleanOrder(Integer displayOrder) {
        if (displayOrder == null || displayOrder < 1) {
            throw validationError();
        }
        return displayOrder;
    }

    private static String processLabel(ProcessEntity process) {
        return process.getProcessSeq() + ". " + process.getName();
    }

    private LocalDateTime now() {
        return KstTime.toLocal(clock.instant());
    }

    private static DomainException lineNotFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "LINE_NOT_FOUND", "라인을 찾을 수 없습니다.");
    }

    private static DomainException duplicateName() {
        return new DomainException(HttpStatus.CONFLICT, "LINE_NAME_DUPLICATE", "이미 사용 중인 라인 이름입니다.");
    }

    private static DomainException validationError() {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
    }
}

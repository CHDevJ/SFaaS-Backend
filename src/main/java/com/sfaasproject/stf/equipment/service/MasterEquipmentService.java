package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.UniqueViolations;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.equipment.entity.EquipmentEntity;
import com.sfaasproject.stf.equipment.entity.EquipmentEntity.Profile;
import com.sfaasproject.stf.equipment.entity.LineEntity;
import com.sfaasproject.stf.equipment.entity.ProcessEntity;
import com.sfaasproject.stf.equipment.repository.EquipmentRepository;
import com.sfaasproject.stf.equipment.repository.LineRepository;
import com.sfaasproject.stf.equipment.repository.ProcessRepository;
import com.sfaasproject.stf.equipment.service.EquipmentPhotoValidationService.Format;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * MST-001~004 설비 기준정보. 바꾸는 작업은 라인 줄을 먼저 잠그고 설비 줄을 잠근다(배치 변경 MST-009 와 같은 순서).
 * 변경 이력은 같은 트랜잭션에 남기고, 삭제한 사람도 변경 이력에 남긴다(equipment 에 삭제한 사람 컬럼이 없음).
 *
 * <p>openapi v7 을 따른다. 길이는 ERD 컬럼에 맞춘다(API_SPEC 11.0, 2026-11-18 사용자 결정): 코드 20, 이름 100,
 * 종류 50, 시리얼 50, 통신 방식 1~20자 문자열. 등록 일시는 선택이며 비우면 현재 시각이다. 사진은 v7 대로 JPG·PNG·WebP 5MB
 * 이하를 서버 폴더에 저장하고, photoUrl 은 10분 동안 열리는 MST-018 서명 주소다(2026-11-18 사용자 결정).
 * 라인·공정을 찾지 못하면 v7 응답 정의에 맞춰 400 VALIDATION_ERROR 로 알린다.
 */
@Service
public class MasterEquipmentService {

    static final int CODE_MAX = 20;
    static final int NAME_MAX = 100;
    static final int TYPE_MAX = 50;
    static final int SERIAL_MAX = 50;
    static final int COMMUNICATION_METHOD_MAX = 20;

    /**
     * 등록·수정 입력. equipmentCode 는 등록할 때만 쓴다.
     *
     * @param registeredAt 비우면 등록은 현재 시각, 수정은 원래 값을 그대로 둔다
     */
    public record Command(String equipmentCode, String equipmentName, String equipmentType, String serialNumber,
            Long lineId, Integer processOrder, String communicationMethod, OffsetDateTime registeredAt,
            MultipartFile photo, boolean removePhoto) { }

    /** MST-001 결과. 설비 수는 lineId 조건만 따르고 equipmentId·페이지와는 상관없다. */
    public record EquipmentPage(long registeredCount, long monitoredCount, Page<MasterEquipmentView> items) { }

    private record Placement(LineEntity line, ProcessEntity process) { }

    private final EquipmentRepository equipment;
    private final LineRepository lines;
    private final ProcessRepository processes;
    private final EquipmentRuntimeQuery runtime;
    private final EquipmentPhotoValidationService photoValidation;
    private final EquipmentPhotoStorage photoStorage;
    private final EquipmentPhotoUrlSigner photoUrls;
    private final AuditRecorder audit;
    private final Clock clock;

    public MasterEquipmentService(EquipmentRepository equipment, LineRepository lines, ProcessRepository processes,
            EquipmentRuntimeQuery runtime, EquipmentPhotoValidationService photoValidation,
            EquipmentPhotoStorage photoStorage, EquipmentPhotoUrlSigner photoUrls, AuditRecorder audit,
            Clock clock) {
        this.equipment = equipment;
        this.lines = lines;
        this.processes = processes;
        this.runtime = runtime;
        this.photoValidation = photoValidation;
        this.photoStorage = photoStorage;
        this.photoUrls = photoUrls;
        this.audit = audit;
        this.clock = clock;
    }

    /** MST-001. 사용 중인 설비만 보인다. 없는 lineId·equipmentId 는 빈 목록이다(v7 은 200/401 만 정의). */
    @Transactional(readOnly = true)
    public EquipmentPage list(Long lineId, Long equipmentId, PageQuery page) {
        Page<EquipmentEntity> found = equipment.search(lineId, equipmentId, page.toPageable(Sort.unsorted()));
        Map<Long, ProcessEntity> processById = processes.findAllById(
                        found.getContent().stream().map(EquipmentEntity::getProcessId).distinct().toList())
                .stream().collect(Collectors.toMap(ProcessEntity::getId, Function.identity()));
        Map<Long, LineEntity> lineById = lines.findAllById(
                        processById.values().stream().map(ProcessEntity::getLineId).distinct().toList())
                .stream().collect(Collectors.toMap(LineEntity::getId, Function.identity()));
        Map<Long, EquipmentRuntimeQuery.Runtime> states =
                runtime.find(found.getContent().stream().map(EquipmentEntity::getId).toList());
        Page<MasterEquipmentView> items = found.map(item -> {
            ProcessEntity process = processById.get(item.getProcessId());
            return view(item, new Placement(lineById.get(process.getLineId()), process), states.get(item.getId()));
        });
        return new EquipmentPage(equipment.countActive(lineId), equipment.countMonitored(lineId), items);
    }

    /** MST-002. */
    @Transactional
    public MasterEquipmentView create(Command command, long actorId) {
        String code = required(command.equipmentCode(), CODE_MAX);
        Profile input = profileInput(command, now());
        Format photoFormat = command.photo() == null ? null : photoValidation.validate(command.photo());
        if (command.removePhoto()) {
            throw validationError();
        }
        Placement placement = place(command.lineId(), command.processOrder());
        if (equipment.existsByEquipCode(code)) {
            throw duplicateCode();
        }
        Profile profile = withProcess(input, placement.process().getId());
        EquipmentEntity created;
        try {
            created = equipment.saveAndFlush(EquipmentEntity.register(code, profile, now()));
        } catch (DataIntegrityViolationException raced) {
            // 같은 코드를 동시에 등록하면 UNIQUE 제약이 막는다. 길이 초과 같은 다른 DB 오류는 그대로 던진다.
            if (UniqueViolations.isUniqueViolation(raced)) {
                throw duplicateCode();
            }
            throw raced;
        }
        if (photoFormat != null) {
            created.changePhoto(photoStorage.save(command.photo(), photoFormat), now());
        }
        audit.record(actorId, AuditTargetType.EQUIPMENT, code, "설비 등록", null, created.getName());
        return view(created, placement, null);
    }

    /** MST-003. 설비 코드는 바꾸지 않는다. 바뀐 항목만 변경 이력에 남긴다. */
    @Transactional
    public MasterEquipmentView update(long equipmentId, Command command, long actorId) {
        LocalDateTime now = now();
        Profile input = profileInput(command, null);
        Format photoFormat = command.photo() == null ? null : photoValidation.validate(command.photo());
        if (photoFormat != null && command.removePhoto()) {
            throw validationError();
        }
        Placement placement = place(command.lineId(), command.processOrder());
        EquipmentEntity target = equipment.findForUpdate(equipmentId).filter(e -> !e.isDeleted())
                .orElseThrow(MasterEquipmentService::equipmentNotFound);
        Profile before = target.profile();
        Profile after = new Profile(input.name(), input.equipType(), input.serialNo(), placement.process().getId(),
                input.commType(), input.registeredAt() == null ? before.registeredAt() : input.registeredAt());
        String code = target.getEquipCode();
        if (!after.equals(before)) {
            target.update(after, now);
            recordIfChanged(actorId, code, "이름", before.name(), after.name());
            recordIfChanged(actorId, code, "종류", before.equipType(), after.equipType());
            recordIfChanged(actorId, code, "시리얼 번호", before.serialNo(), after.serialNo());
            if (before.processId() != after.processId()) {
                audit.record(actorId, AuditTargetType.EQUIPMENT, code, "공정 배치",
                        placementLabel(before.processId()), label(placement));
            }
            recordIfChanged(actorId, code, "통신 방식", before.commType(), after.commType());
            recordIfChanged(actorId, code, "등록 일시", String.valueOf(before.registeredAt()),
                    String.valueOf(after.registeredAt()));
        }
        String oldPhoto = target.getPhotoPath();
        if (photoFormat != null) {
            target.changePhoto(photoStorage.save(command.photo(), photoFormat), now);
            photoStorage.deleteAfterCommit(oldPhoto);
            audit.record(actorId, AuditTargetType.EQUIPMENT, code, "사진", oldPhoto == null ? "없음" : "있음", "새 사진");
        } else if (command.removePhoto() && oldPhoto != null) {
            target.changePhoto(null, now);
            photoStorage.deleteAfterCommit(oldPhoto);
            audit.record(actorId, AuditTargetType.EQUIPMENT, code, "사진", "있음", "없음");
        }
        equipment.flush();
        return view(target, placement, runtime.find(java.util.List.of(equipmentId)).get(equipmentId));
    }

    /**
     * MST-004. 종료되지 않은 알람이 있으면 거부한다. 설비와 함께 데이터 항목과 장착 부품도 논리 삭제하고,
     * 지난 알람과 교체 이력은 그대로 둔다(화면설계서 P07). 사진 파일은 삭제한 설비 조회를 위해 남긴다.
     */
    @Transactional
    public void delete(long equipmentId, long actorId) {
        EquipmentEntity target = equipment.findForUpdate(equipmentId).filter(e -> !e.isDeleted())
                .orElseThrow(MasterEquipmentService::equipmentNotFound);
        if (runtime.hasActiveAlarm(equipmentId)) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "EQUIPMENT_HAS_ACTIVE_ALARM",
                    "발생 중인 알람이 있어 삭제할 수 없습니다.");
        }
        LocalDateTime now = now();
        target.markDeleted(now);
        equipment.flush();
        int items = equipment.deleteDataItemsOf(equipmentId, now);
        int parts = equipment.deletePartsOf(equipmentId, now);
        audit.record(actorId, AuditTargetType.EQUIPMENT, target.getEquipCode(), "설비 삭제", "사용",
                "삭제(데이터 항목 " + items + "개, 장착 부품 " + parts + "개 함께 삭제)");
    }

    /** EQP-004 의 basicInfo. 사용 중인 설비가 아니면(없음·삭제·라인 삭제) 비어 있다. */
    @Transactional(readOnly = true)
    public java.util.Optional<MasterEquipmentView> findActive(long equipmentId) {
        return equipment.findById(equipmentId).filter(item -> !item.isDeleted()).flatMap(item ->
                processes.findById(item.getProcessId()).flatMap(process ->
                        lines.findByIdAndDeletedAtIsNull(process.getLineId()).map(line -> view(item,
                                new Placement(line, process), runtime.find(List.of(item.getId())).get(item.getId())))));
    }

    private MasterEquipmentView view(EquipmentEntity item, Placement placement, EquipmentRuntimeQuery.Runtime state) {
        String photoUrl = item.getPhotoPath() == null ? null : photoUrls.url(item.getPhotoPath(), clock.instant());
        return new MasterEquipmentView(item.getId(), item.getEquipCode(), item.getName(), item.getEquipType(),
                item.getSerialNo(), placement.line().getId(), placement.line().getName(), placement.process().getId(),
                placement.process().getName(), placement.process().getProcessSeq(), item.getCommType(), photoUrl,
                item.getRegisteredAt(), state == null ? null : state.lastReceivedAt());
    }

    /** 사용 중인 라인을 잠그고 그 라인에 이미 있는 공정 순서를 찾는다(MST-009 와 같은 규칙). */
    private Placement place(Long lineId, Integer processOrder) {
        if (lineId == null || processOrder == null || processOrder < 1) {
            throw validationError();
        }
        // 요청 본문의 라인·공정이 없으면 v7 MST-002/003 응답 정의에 맞춰 400 으로 알린다.
        LineEntity line = lines.findActiveForUpdate(lineId).orElseThrow(MasterEquipmentService::validationError);
        ProcessEntity process = processes.findFirstByLineIdAndProcessSeqOrderByIdAsc(lineId, processOrder)
                .orElseThrow(MasterEquipmentService::validationError);
        return new Placement(line, process);
    }

    /** 입력값 검증. processId 는 아직 모르므로 0 으로 둔다. registeredAt 이 없으면 defaultRegisteredAt. */
    private Profile profileInput(Command command, LocalDateTime defaultRegisteredAt) {
        String name = required(command.equipmentName(), NAME_MAX);
        String type = required(command.equipmentType(), TYPE_MAX);
        String serial = optional(command.serialNumber(), SERIAL_MAX);
        String method = required(command.communicationMethod(), COMMUNICATION_METHOD_MAX);
        LocalDateTime registeredAt = command.registeredAt() == null ? defaultRegisteredAt
                : KstTime.toLocal(command.registeredAt().toInstant()).truncatedTo(ChronoUnit.SECONDS);
        return new Profile(name, type, serial, 0, method, registeredAt);
    }

    private static Profile withProcess(Profile input, long processId) {
        return new Profile(input.name(), input.equipType(), input.serialNo(), processId, input.commType(),
                input.registeredAt());
    }

    private void recordIfChanged(long actorId, String code, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            audit.record(actorId, AuditTargetType.EQUIPMENT, code, field, before, after);
        }
    }

    private String placementLabel(long processId) {
        return processes.findById(processId)
                .map(process -> lines.findById(process.getLineId())
                        .map(line -> label(new Placement(line, process))).orElse(String.valueOf(processId)))
                .orElse(String.valueOf(processId));
    }

    private static String label(Placement placement) {
        return placement.line().getName() + " / " + placement.process().getProcessSeq() + ". "
                + placement.process().getName();
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

    private LocalDateTime now() {
        return KstTime.toLocal(clock.instant()).truncatedTo(ChronoUnit.SECONDS);
    }

    private static DomainException equipmentNotFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "설비를 찾을 수 없습니다.");
    }

    private static DomainException duplicateCode() {
        return new DomainException(HttpStatus.CONFLICT, "EQUIPMENT_CODE_DUPLICATE", "이미 사용 중인 설비 코드입니다.");
    }

    private static DomainException validationError() {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
    }
}

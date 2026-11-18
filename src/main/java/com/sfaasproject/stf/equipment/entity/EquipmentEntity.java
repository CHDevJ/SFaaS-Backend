package com.sfaasproject.stf.equipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD equipment. 라인은 공정(process_id)을 거쳐서 찾는다. 설비 코드는 등록한 뒤 바꿀 수 없고,
 * 삭제한 설비의 코드도 다시 쓸 수 없다. 삭제는 줄을 지우지 않고 deleted_at 을 채우며,
 * 채워진 설비는 감시와 알람과 배치도에서 제외한다.
 */
@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_id")
    private Long id;

    @Column(name = "equip_code", length = 20, nullable = false, unique = true, updatable = false)
    private String equipCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "equip_type", length = 50, nullable = false)
    private String equipType;

    @Column(name = "serial_no", length = 50)
    private String serialNo;

    @Column(name = "process_id", nullable = false)
    private Long processId;

    /** MODBUS_TCP, OPC_UA, MQTT. */
    @Column(name = "comm_type", length = 20, nullable = false)
    private String commType;

    /** 저장한 사진의 파일 이름. 경로는 서버 설정(app.equipment.photo-dir)으로 정한다. */
    @Column(name = "photo_path", length = 255)
    private String photoPath;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 설비의 바꿀 수 있는 정보. 코드와 사진은 따로 다룬다. */
    public record Profile(String name, String equipType, String serialNo, long processId, String commType,
            LocalDateTime registeredAt) { }

    private EquipmentEntity(String equipCode, Profile profile, LocalDateTime now) {
        this.equipCode = equipCode;
        this.createdAt = now;
        apply(profile, now);
    }

    public static EquipmentEntity register(String equipCode, Profile profile, LocalDateTime now) {
        return new EquipmentEntity(equipCode, profile, now);
    }

    public Profile profile() {
        return new Profile(name, equipType, serialNo, processId, commType, registeredAt);
    }

    public void update(Profile profile, LocalDateTime now) {
        apply(profile, now);
    }

    public void moveToProcess(long processId, LocalDateTime now) {
        this.processId = processId;
        this.updatedAt = now;
    }

    public void changePhoto(String photoPath, LocalDateTime now) {
        this.photoPath = photoPath;
        this.updatedAt = now;
    }

    public void markDeleted(LocalDateTime now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void apply(Profile profile, LocalDateTime now) {
        this.name = profile.name();
        this.equipType = profile.equipType();
        this.serialNo = profile.serialNo();
        this.processId = profile.processId();
        this.commType = profile.commType();
        this.registeredAt = profile.registeredAt();
        this.updatedAt = now;
    }
}

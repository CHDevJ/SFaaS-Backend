package com.sfaasproject.stf.equipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD equipment 중 라인 배치에 필요한 컬럼만 매핑한다. 설비 등록·수정(MST-001~004)을 구현할 때 나머지 컬럼을 더한다.
 * 라인은 공정(process_id)을 거쳐서 찾는다. 삭제된 설비(deleted_at)는 배치와 감시에서 제외한다.
 */
@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentEntity {

    @Id
    @Column(name = "equipment_id")
    private Long id;

    @Column(name = "equip_code", length = 20, nullable = false, unique = true, updatable = false)
    private String equipCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "process_id", nullable = false)
    private Long processId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void moveToProcess(long processId, LocalDateTime now) {
        this.processId = processId;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.entity.EquipmentEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, Long> {

    List<EquipmentEntity> findByProcessIdInAndDeletedAtIsNull(Collection<Long> processIds);

    /** 라인의 공정에 사용 중인 설비가 한 대라도 있는지. 삭제된 설비는 세지 않는다. */
    @Query("""
            select count(e) > 0 from EquipmentEntity e
            where e.deletedAt is null
              and e.processId in (select p.id from ProcessEntity p where p.lineId = :lineId)
            """)
    boolean existsActiveInLine(@Param("lineId") long lineId);

    /** 배치를 바꿀 설비를 잠그고 읽는다. 잠그는 순서를 같게 하려고 번호순으로 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EquipmentEntity e where e.id in :ids order by e.id")
    List<EquipmentEntity> lockAllById(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EquipmentEntity e where e.id = :id")
    Optional<EquipmentEntity> findForUpdate(@Param("id") long id);

    /** DB 의 equip_code 는 UNIQUE 이고 삭제한 설비도 줄이 남으므로 삭제 여부와 상관없이 검사한다. */
    boolean existsByEquipCode(String equipCode);

    /**
     * MST-001 목록. 사용 중인 설비를 라인 표시순서, 공정 순서, 설비 번호순으로 정렬한다.
     */
    @Query(value = """
            select e from EquipmentEntity e, ProcessEntity p, LineEntity l
            where e.processId = p.id and p.lineId = l.id
              and (:lineId is null or l.id = :lineId)
              and (:equipmentId is null or e.id = :equipmentId)
              and e.deletedAt is null
            order by l.sortOrder, l.id, p.processSeq, e.id
            """,
            countQuery = """
            select count(e) from EquipmentEntity e, ProcessEntity p
            where e.processId = p.id
              and (:lineId is null or p.lineId = :lineId)
              and (:equipmentId is null or e.id = :equipmentId)
              and e.deletedAt is null
            """)
    Page<EquipmentEntity> search(@Param("lineId") Long lineId, @Param("equipmentId") Long equipmentId,
            Pageable pageable);

    /** 사용 중인(삭제되지 않은) 설비 수. lineId 가 없으면 전체. */
    @Query("""
            select count(e) from EquipmentEntity e, ProcessEntity p
            where e.processId = p.id and e.deletedAt is null
              and (:lineId is null or p.lineId = :lineId)
            """)
    long countActive(@Param("lineId") Long lineId);

    /** 사용 중인 설비 중 사용 중인 데이터 항목이 하나 이상 있어 값을 받는 설비 수(MST-001 monitoredCount). */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) FROM equipment e JOIN process p ON p.process_id = e.process_id
            WHERE e.deleted_at IS NULL
              AND (:lineId IS NULL OR p.line_id = :lineId)
              AND EXISTS (SELECT 1 FROM data_item d WHERE d.equipment_id = e.equipment_id AND d.deleted_at IS NULL)
            """)
    long countMonitored(@Param("lineId") Long lineId);

    /** 설비를 삭제할 때 그 설비의 데이터 항목을 함께 논리 삭제한다(화면설계서 P07). */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE data_item SET deleted_at = :now, updated_at = :now
            WHERE equipment_id = :equipmentId AND deleted_at IS NULL
            """)
    int deleteDataItemsOf(@Param("equipmentId") long equipmentId, @Param("now") LocalDateTime now);

    /** 설비를 삭제할 때 그 설비의 장착 부품을 함께 논리 삭제한다. 교체 이력은 남는다. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE part SET deleted_at = :now, updated_at = :now
            WHERE equipment_id = :equipmentId AND deleted_at IS NULL
            """)
    int deletePartsOf(@Param("equipmentId") long equipmentId, @Param("now") LocalDateTime now);
}

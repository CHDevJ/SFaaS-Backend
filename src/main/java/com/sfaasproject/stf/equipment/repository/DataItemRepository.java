package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.entity.DataItemEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataItemRepository extends JpaRepository<DataItemEntity, Long> {

    /** MST-010. 사용 중인 항목을 표시 순서, 번호순으로. */
    List<DataItemEntity> findByEquipmentIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long equipmentId);

    /** DB 의 (equipment_id, item_code) 는 UNIQUE 이고 삭제한 항목도 줄이 남으므로 삭제 여부와 상관없이 검사한다. */
    boolean existsByEquipmentIdAndItemCode(Long equipmentId, String itemCode);

    /** 새 항목은 그 설비의 마지막 표시 순서 다음에 둔다. 항목이 없으면 0. */
    @Query("select coalesce(max(d.sortOrder), 0) from DataItemEntity d where d.equipmentId = :equipmentId")
    int maxSortOrder(@Param("equipmentId") long equipmentId);

    /** 바꿀 항목을 잠그고 읽는다. 설비 줄을 먼저 잠근 뒤 호출한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DataItemEntity d where d.id = :id and d.deletedAt is null")
    Optional<DataItemEntity> findActiveForUpdate(@Param("id") long id);

    @Query("select d.equipmentId from DataItemEntity d where d.id = :id and d.deletedAt is null")
    Optional<Long> findActiveEquipmentId(@Param("id") long id);
}

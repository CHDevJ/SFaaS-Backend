package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.entity.EquipmentEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}

package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.entity.LineEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineRepository extends JpaRepository<LineEntity, Long> {

    /** 사용 중인 라인을 표시순서, 번호순으로 읽는다. */
    List<LineEntity> findByDeletedAtIsNullOrderBySortOrderAscIdAsc();

    Optional<LineEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 사용 중인 라인을 잠그고 읽는다. 라인 수정·삭제·배치 변경이 같은 라인을 동시에 바꾸지 못하게 한다.
     * 설비를 이 라인에 넣는 작업(설비 등록·수정)도 같은 라인 줄을 먼저 잠가야 삭제와 엇갈리지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LineEntity l where l.id = :id and l.deletedAt is null")
    Optional<LineEntity> findActiveForUpdate(@Param("id") long id);

    /** DB 의 line.name 은 UNIQUE 이고 삭제한 라인도 줄이 남으므로 삭제 여부와 상관없이 검사한다. */
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}

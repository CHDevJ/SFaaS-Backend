package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.entity.ProcessEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessRepository extends JpaRepository<ProcessEntity, Long> {

    List<ProcessEntity> findByLineIdInOrderByLineIdAscProcessSeqAscIdAsc(Collection<Long> lineIds);

    List<ProcessEntity> findByLineIdOrderByProcessSeqAscIdAsc(Long lineId);

    /** 라인 안에서 해당 순서의 공정. 같은 순서가 여럿이면 번호가 가장 작은 공정이다. */
    Optional<ProcessEntity> findFirstByLineIdAndProcessSeqOrderByIdAsc(Long lineId, int processSeq);
}

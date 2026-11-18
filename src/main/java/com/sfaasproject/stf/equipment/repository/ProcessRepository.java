package com.sfaasproject.stf.equipment.repository;

import com.sfaasproject.stf.equipment.entity.ProcessEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessRepository extends JpaRepository<ProcessEntity, Long> {

    List<ProcessEntity> findByLineIdInOrderByLineIdAscProcessSeqAscIdAsc(Collection<Long> lineIds);

    List<ProcessEntity> findByLineIdOrderByProcessSeqAscIdAsc(Long lineId);
}

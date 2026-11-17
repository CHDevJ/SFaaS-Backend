package com.sfaasproject.stf.system.repository;

import com.sfaasproject.stf.system.entity.ChangeLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeLogRepository extends JpaRepository<ChangeLogEntity, Long> {
}

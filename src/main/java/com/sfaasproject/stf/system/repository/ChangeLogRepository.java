package com.sfaasproject.stf.system.repository;

import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.entity.ChangeLogEntity;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChangeLogRepository extends JpaRepository<ChangeLogEntity, Long> {

    /**
     * AUD-001. from 이상 to 미만, 최근 변경부터. keyword 는 소문자로 바꾸고 %·_·! 를 ! 로 막은 LIKE 패턴이며
     * 대상·항목·이전 값·변경 값 중 하나에 들어 있으면 찾는다.
     */
    @Query(value = """
            select c from ChangeLogEntity c
            where c.changedAt >= :from and c.changedAt < :to
              and (:type is null or c.targetType = :type)
              and (:keyword is null
                   or lower(c.targetName) like :keyword escape '!'
                   or lower(c.fieldName) like :keyword escape '!'
                   or lower(c.oldValue) like :keyword escape '!'
                   or lower(c.newValue) like :keyword escape '!')
            order by c.changedAt desc, c.id desc
            """,
            countQuery = """
            select count(c) from ChangeLogEntity c
            where c.changedAt >= :from and c.changedAt < :to
              and (:type is null or c.targetType = :type)
              and (:keyword is null
                   or lower(c.targetName) like :keyword escape '!'
                   or lower(c.fieldName) like :keyword escape '!'
                   or lower(c.oldValue) like :keyword escape '!'
                   or lower(c.newValue) like :keyword escape '!')
            """)
    Page<ChangeLogEntity> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("type") AuditTargetType type, @Param("keyword") String keyword, Pageable pageable);
}

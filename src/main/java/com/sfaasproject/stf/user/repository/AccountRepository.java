package com.sfaasproject.stf.user.repository;

import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.entity.ApprovalStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByLoginId(String loginId);

    /** 삭제한 계정의 아이디도 다시 쓸 수 없으므로 삭제 여부와 상관없이 검사한다. */
    boolean existsByLoginId(String loginId);

    /** 삭제되지 않은 계정을 승인 상태별로 조회한다(승인 대기 목록). */
    Page<AccountEntity> findByApprovalStatusAndDeletedAtIsNull(ApprovalStatus status, Pageable pageable);

    /** 승인과 거절이 같은 신청을 동시에 처리하지 못하도록 줄을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id = :id")
    Optional<AccountEntity> findForUpdate(@Param("id") long id);
}

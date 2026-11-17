package com.sfaasproject.stf.user.repository;

import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.entity.ApprovalStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
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

    /**
     * 승인되고 삭제되지 않은 계정을 조건으로 찾는다(계정 관리 목록).
     *
     * @param pattern 소문자로 바꾼 LIKE 패턴. 와일드카드 문자는 '!' 로 이스케이프해서 넘긴다
     */
    @Query("""
            select a from AccountEntity a
            where a.deletedAt is null
              and a.approvalStatus = com.sfaasproject.stf.user.entity.ApprovalStatus.APPROVED
              and a.role in :roles
              and a.mustChangePassword in :passwordFlags
              and (lower(a.name) like :pattern escape '!' or lower(a.loginId) like :pattern escape '!')
            """)
    Page<AccountEntity> searchApproved(@Param("roles") Collection<String> roles,
            @Param("passwordFlags") Collection<Boolean> passwordFlags, @Param("pattern") String pattern,
            Pageable pageable);

    /**
     * 사용 중인(승인되고 삭제되지 않은) 시스템관리자를 모두 잠그고 읽는다. 마지막 시스템관리자를 지키는 검사를
     * 두 요청이 동시에 통과하지 못하게 하려고 잠근다. 잠그는 순서를 같게 하려고 번호순으로 읽는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from AccountEntity a
            where a.deletedAt is null
              and a.approvalStatus = com.sfaasproject.stf.user.entity.ApprovalStatus.APPROVED
              and a.role = 'ADMIN'
            order by a.id
            """)
    List<AccountEntity> lockActiveSystemAdmins();

    /** 승인과 거절이 같은 신청을 동시에 처리하지 못하도록 줄을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id = :id")
    Optional<AccountEntity> findForUpdate(@Param("id") long id);
}

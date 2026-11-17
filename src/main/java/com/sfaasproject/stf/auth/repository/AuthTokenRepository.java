package com.sfaasproject.stf.auth.repository;

import com.sfaasproject.stf.auth.entity.AuthTokenEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthTokenEntity, Long> {

    /** 회전할 때 같은 토큰을 두 요청이 동시에 쓰지 못하도록 줄을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuthTokenEntity t where t.tokenHash = :hash")
    Optional<AuthTokenEntity> findForRotation(@Param("hash") String hash);

    Optional<AuthTokenEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthTokenEntity t set t.revokedAt = :at where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("at") LocalDateTime at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthTokenEntity t set t.revokedAt = :at where t.accountId = :accountId and t.revokedAt is null")
    int revokeAllOfAccount(@Param("accountId") Long accountId, @Param("at") LocalDateTime at);
}

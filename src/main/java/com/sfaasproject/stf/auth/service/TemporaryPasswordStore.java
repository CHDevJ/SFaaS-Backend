package com.sfaasproject.stf.auth.service;

import java.time.Instant;

/**
 * 관리자가 발급한 임시 비밀번호의 만료 시각을 기억한다(USR-005, 30분).
 * 기록이 없는 계정(예: 초기 데이터로 만든 계정)은 만료를 적용하지 않는다.
 */
public interface TemporaryPasswordStore {

    void issued(long userId, Instant expiresAt);

    /** 기록이 있고 그 시각이 지났으면 true. */
    boolean isExpired(long userId, Instant now);

    /** 사용자가 비밀번호를 바꾸거나 임시 비밀번호가 무효가 되면 기록을 지운다. */
    void clear(long userId);
}

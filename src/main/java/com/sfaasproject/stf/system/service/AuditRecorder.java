package com.sfaasproject.stf.system.service;

import com.sfaasproject.stf.system.entity.AuditTargetType;

/**
 * 변경 이력 기록 포트. 설비·설정·계정 등을 바꾸는 서비스가 같은 트랜잭션 안에서 호출한다.
 * 기록에 실패하면 호출한 변경도 함께 취소되어야 한다. 비밀번호와 토큰 값은 넘기지 않는다.
 */
public interface AuditRecorder {

    /**
     * @param changedBy 변경한 사람의 계정 번호
     * @param targetName 바꾼 대상 (예: 가입 신청자의 아이디)
     * @param fieldName 바꾼 항목 (예: 가입 승인)
     * @param oldValue 이전 값. 없으면 null
     * @param newValue 변경 값. 없으면 null
     */
    void record(long changedBy, AuditTargetType targetType, String targetName, String fieldName,
            String oldValue, String newValue);
}

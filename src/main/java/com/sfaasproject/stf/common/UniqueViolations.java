package com.sfaasproject.stf.common;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 저장이 DB 제약에 걸렸을 때 UNIQUE(중복) 위반인지 가린다. 실패한 flush 뒤에는 같은 세션으로 다시 조회하면
 * Hibernate 가 AssertionFailure 를 내므로, 다시 조회하지 않고 원인 SQLException 의 SQLState 로 판단한다.
 * SQLState 23xxx 는 무결성 제약 위반(MySQL 1062 중복은 23000, H2 는 23505)이고,
 * 길이 초과(22001) 같은 값 오류는 중복이 아니다.
 */
public final class UniqueViolations {

    private UniqueViolations() {
    }

    public static boolean isUniqueViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                if (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState())) {
                    return true;
                }
                if (!sql.getSQLState().startsWith("23")) {
                    return false;
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}

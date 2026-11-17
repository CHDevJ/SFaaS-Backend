package com.sfaasproject.stf.system.service;

import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.entity.ChangeLogEntity;
import com.sfaasproject.stf.system.repository.ChangeLogRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** change_log 테이블에 쓰는 {@link AuditRecorder}. 호출한 서비스의 트랜잭션에 반드시 참여한다. */
@Component
public class DatabaseAuditRecorder implements AuditRecorder {

    private final ChangeLogRepository changeLogs;
    private final Clock clock;

    public DatabaseAuditRecorder(ChangeLogRepository changeLogs, Clock clock) {
        this.changeLogs = changeLogs;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long changedBy, AuditTargetType targetType, String targetName, String fieldName,
            String oldValue, String newValue) {
        changeLogs.save(new ChangeLogEntity(KstTime.toLocal(clock.instant()), changedBy, targetType,
                fit(targetName, ChangeLogEntity.NAME_MAX), fit(fieldName, ChangeLogEntity.FIELD_MAX),
                fit(oldValue, ChangeLogEntity.VALUE_MAX), fit(newValue, ChangeLogEntity.VALUE_MAX)));
    }

    /** 컬럼 길이를 넘는 값은 잘라서 저장한다. 길이 때문에 이력 기록이 실패하면 안 된다. */
    static String fit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}

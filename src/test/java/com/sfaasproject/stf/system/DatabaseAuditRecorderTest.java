package com.sfaasproject.stf.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sfaasproject.stf.auth.AbstractAuthIntegrationTest;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.entity.ChangeLogEntity;
import com.sfaasproject.stf.system.service.AuditRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class DatabaseAuditRecorderTest extends AbstractAuthIntegrationTest {

    @Autowired
    private AuditRecorder audit;
    @Autowired
    private TransactionTemplate transaction;

    @Test
    void a_record_is_saved_when_the_caller_has_a_transaction() {
        transaction.executeWithoutResult(status ->
                audit.record(7, AuditTargetType.SETTING, "알람 판정 횟수", "연속 횟수", "3", "5"));

        assertThat(changeLogs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getChangedBy()).isEqualTo(7);
            assertThat(log.getTargetType()).isEqualTo(AuditTargetType.SETTING);
            assertThat(log.getTargetName()).isEqualTo("알람 판정 횟수");
            assertThat(log.getFieldName()).isEqualTo("연속 횟수");
            assertThat(log.getOldValue()).isEqualTo("3");
            assertThat(log.getNewValue()).isEqualTo("5");
            assertThat(log.getChangedAt()).isNotNull();
        });
    }

    @Test
    void recording_without_the_callers_transaction_is_refused() {
        assertThatThrownBy(() -> audit.record(7, AuditTargetType.ACCOUNT, "x", "y", null, null))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(changeLogs.count()).isZero();
    }

    @Test
    void values_longer_than_the_columns_are_cut_instead_of_failing() {
        transaction.executeWithoutResult(status -> audit.record(7, AuditTargetType.ACCOUNT,
                "가".repeat(300), "나".repeat(300), "다".repeat(900), "라".repeat(900)));

        ChangeLogEntity log = changeLogs.findAll().get(0);
        assertThat(log.getTargetName()).hasSize(ChangeLogEntity.NAME_MAX);
        assertThat(log.getFieldName()).hasSize(ChangeLogEntity.FIELD_MAX);
        assertThat(log.getOldValue()).hasSize(ChangeLogEntity.VALUE_MAX);
        assertThat(log.getNewValue()).hasSize(ChangeLogEntity.VALUE_MAX);
    }

    @Test
    void empty_old_and_new_values_are_allowed() {
        transaction.executeWithoutResult(status ->
                audit.record(7, AuditTargetType.DUTY, "당번", "신규 등록", null, null));

        assertThat(changeLogs.findAll().get(0).getOldValue()).isNull();
    }
}

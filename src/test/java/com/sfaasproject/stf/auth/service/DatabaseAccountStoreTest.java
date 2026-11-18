package com.sfaasproject.stf.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sfaasproject.stf.auth.AbstractAuthIntegrationTest;
import com.sfaasproject.stf.auth.service.AuthFlowService.Account;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.user.entity.AccountEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DatabaseAccountStoreTest extends AbstractAuthIntegrationTest {

    @Autowired
    private DatabaseAccountStore store;

    @Test
    void an_approved_account_is_active_with_its_position() {
        AccountEntity entity = approvedAccount("admin01", "ADMIN");

        Account account = store.findByLoginId("admin01");

        assertThat(account.userId()).isEqualTo(entity.getId());
        assertThat(account.permission()).isEqualTo(Permission.ADMIN);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.passwordHash()).isEqualTo(entity.getPasswordHash());
        assertThat(account.lastLoginAt()).isNull();
        assertThat(store.findById(entity.getId()).loginId()).isEqualTo("admin01");
    }

    @Test
    void a_pending_account_has_no_position_and_is_pending_approval() {
        pendingAccount("waiting1");

        Account account = store.findByLoginId("waiting1");

        assertThat(account.status()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(account.permission()).isNull();
    }

    @Test
    void a_temporary_password_makes_the_status_password_change_required() {
        AccountEntity entity = approvedAccount("worker01", "USER");
        entity.requirePasswordChange(LocalDateTime.now(clock));
        accounts.save(entity);

        assertThat(store.findByLoginId("worker01").status()).isEqualTo(AccountStatus.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    void deleted_and_unknown_accounts_are_not_found() {
        AccountEntity entity = approvedAccount("gone0001", "USER");
        entity.markDeleted(LocalDateTime.now(clock));
        accounts.save(entity);

        assertThat(store.findByLoginId("gone0001")).isNull();
        assertThat(store.findById(entity.getId())).isNull();
        assertThat(store.findByLoginId("nobody01")).isNull();
        assertThat(store.findById(123456789L)).isNull();
    }

    @Test
    void an_unknown_role_value_is_treated_as_no_position() {
        approvedAccount("odd00001", "NOT_A_ROLE");

        assertThat(store.findByLoginId("odd00001").permission()).isNull();
    }

    @Test
    void recording_a_login_stores_the_time_in_korean_local_time_and_reads_it_back_as_the_same_instant() {
        AccountEntity entity = approvedAccount("admin01", "ADMIN");
        Instant at = Instant.parse("2026-11-17T01:30:00Z");

        store.recordSuccessfulLogin(entity.getId(), at);

        AccountEntity reloaded = accounts.findById(entity.getId()).orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 30, 0));
        assertThat(store.findById(entity.getId()).lastLoginAt()).isEqualTo(at);
    }

    @Test
    void changing_the_password_replaces_the_hash_and_clears_the_change_required_flag() {
        AccountEntity entity = approvedAccount("worker01", "USER");
        entity.requirePasswordChange(LocalDateTime.now(clock));
        accounts.save(entity);

        store.changePassword(entity.getId(), "{new-hash}");

        AccountEntity reloaded = accounts.findById(entity.getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo("{new-hash}");
        assertThat(reloaded.isMustChangePassword()).isFalse();
        assertThat(store.findById(entity.getId()).status()).isEqualTo(AccountStatus.ACTIVE);
    }
}

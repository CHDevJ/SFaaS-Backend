package com.sfaasproject.stf.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AbstractAuthIntegrationTest;
import com.sfaasproject.stf.system.entity.ChangeLogEntity;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.event.AccountDeletedEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** USR-001 목록, USR-002 상세, USR-003 수정, USR-004 삭제. */
@RecordApplicationEvents
class SystemUserApiTest extends AbstractAuthIntegrationTest {

    private static final String URL = "/api/v1/system/users";

    @Autowired
    private ApplicationEvents events;

    private AccountEntity admin;
    private String adminBearer;

    @BeforeEach
    void admin() {
        admin = approvedAccount("admin001", "ADMIN");
        adminBearer = bearerFor(admin);
    }

    private AccountEntity named(String loginId, String role, String name) {
        AccountEntity account = approvedAccount(loginId, role);
        account.updateProfile(name, role, LocalDateTime.now(clock));
        return accounts.save(account);
    }

    private String body(String name, String position) {
        return "{\"name\":\"" + name + "\",\"permission\":\"" + position + "\"}";
    }

    // ---- USR-001 목록 ----

    @Test
    void the_list_shows_approved_accounts_only_sorted_by_creation_without_secrets_or_last_login() throws Exception {
        named("manager1", "USER", "Bob");
        named("maint001", "USER", "Carol");
        pendingAccount("waiting1");
        AccountEntity deleted = named("gone0001", "USER", "Dave");
        deleted.markDeleted(LocalDateTime.now(clock));
        accounts.save(deleted);
        AccountEntity adminRow = accounts.findById(admin.getId()).orElseThrow();
        adminRow.updateProfile("Alice", "ADMIN", LocalDateTime.now(clock));
        adminRow.recordLogin(LocalDateTime.now(clock));
        accounts.save(adminRow);

        mockMvc.perform(get(URL).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Carol"))
                .andExpect(jsonPath("$.data[1].name").value("Bob"))
                .andExpect(jsonPath("$.data[2].name").value("Alice"))
                .andExpect(jsonPath("$.data[0].position").doesNotExist())
                .andExpect(jsonPath("$.data[2].permission").value("ADMIN"))
                .andExpect(jsonPath("$.data[1].permission").value("USER"))
                .andExpect(jsonPath("$.data[0].accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].telegramConnected").value(false))
                .andExpect(jsonPath("$.data[0].createdAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data[0].lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data[0].telegramChatId").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }

    @Test
    void the_keyword_matches_name_or_login_id_ignoring_case_and_wildcards_literally() throws Exception {
        named("kimline1", "USER", "Kim Line");
        named("leemaint", "USER", "Lee_Park");
        named("parkjh01", "USER", "Park 100%");

        mockMvc.perform(get(URL).param("keyword", "KIM").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].loginId").value("kimline1"));
        mockMvc.perform(get(URL).param("keyword", "maint").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].loginId").value("leemaint"));
        mockMvc.perform(get(URL).param("keyword", "_").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Lee_Park"));
        mockMvc.perform(get(URL).param("keyword", "%").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Park 100%"));
    }

    @Test
    void permission_and_status_filters_are_direct() throws Exception {
        named("manager1", "USER", "B");
        AccountEntity temp=named("maint001", "USER", "C");temp.requirePasswordChange(LocalDateTime.now(clock));accounts.save(temp);
        mockMvc.perform(get(URL).param("permission","USER").header("Authorization",adminBearer)).andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get(URL).param("permission","ADMIN").header("Authorization",adminBearer)).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get(URL).param("accountStatus","PASSWORD_CHANGE_REQUIRED").header("Authorization",adminBearer)).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void sorting_and_paging_follow_the_contract() throws Exception {
        named("user0001", "USER", "Zed");
        named("user0002", "USER", "Amy");

        mockMvc.perform(get(URL).param("sort", "loginId,desc").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data[0].loginId").value("user0002"))
                .andExpect(jsonPath("$.data[2].loginId").value("admin001"));
        mockMvc.perform(get(URL).param("sort", "name").param("size", "1").param("page", "1")
                        .header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Zed")) // Amy, Zed, 테스터 순서
                .andExpect(jsonPath("$.meta.totalPages").value(3));
    }

    @Test
    void invalid_list_parameters_return_400() throws Exception {
        String[][] invalid = {{"page", "-1"}, {"size", "0"},
                {"permission", "BOSS"}, {"permission", "ROOT"}, {"accountStatus", "SLEEPING"},
                {"keyword", "a".repeat(101)}, {"size", "101"}};
        for (String[] param : invalid) {
            mockMvc.perform(get(URL).param(param[0], param[1]).header("Authorization", adminBearer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void only_the_system_admin_can_manage_accounts() throws Exception {
        AccountEntity manager = approvedAccount("manager1", "USER");
        String bearer = bearerFor(manager);

        mockMvc.perform(get(URL).header("Authorization", bearer)).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/" + admin.getId()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(URL + "/" + manager.getId()).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("x", "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(URL + "/" + admin.getId()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL + "/" + admin.getId() + "/password-reset").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        assertThat(accounts.findById(manager.getId()).orElseThrow().getRole()).isEqualTo("USER");
    }

    // ---- USR-002 상세 ----

    @Test
    void the_detail_includes_the_last_login_time() throws Exception {
        AccountEntity account = approvedAccount("maint001", "USER");
        account.recordLogin(LocalDateTime.now(clock));
        account.connectTelegram("777", LocalDateTime.now(clock));
        accounts.save(account);

        mockMvc.perform(get(URL + "/" + account.getId()).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(account.getId()))
                .andExpect(jsonPath("$.data.position").doesNotExist())
                .andExpect(jsonPath("$.data.telegramConnected").value(true))
                .andExpect(jsonPath("$.data.telegramLinkedAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.lastLoginAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.telegramChatId").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void pending_deleted_and_unknown_accounts_are_not_found() throws Exception {
        AccountEntity pending = pendingAccount("waiting1");
        AccountEntity deleted = approvedAccount("gone0001", "USER");
        deleted.markDeleted(LocalDateTime.now(clock));
        accounts.save(deleted);

        for (long id : new long[] {pending.getId(), deleted.getId(), 987654321L}) {
            mockMvc.perform(get(URL + "/" + id).header("Authorization", adminBearer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
            mockMvc.perform(put(URL + "/" + id).header("Authorization", adminBearer)
                            .contentType(MediaType.APPLICATION_JSON).content(body("x", "USER")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete(URL + "/" + id).header("Authorization", adminBearer))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get(URL + "/abc").header("Authorization", adminBearer)).andExpect(status().isBadRequest());
    }

    // ---- USR-003 수정 ----

    @Test
    void updating_changes_name_and_position_and_records_each_change() throws Exception {
        AccountEntity account = approvedAccount("maint001", "USER");

        mockMvc.perform(put(URL + "/" + account.getId()).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("  New Name  ", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Name"))
                .andExpect(jsonPath("$.data.position").doesNotExist())
                .andExpect(jsonPath("$.data.permission").value("ADMIN"));

        AccountEntity saved = accounts.findById(account.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("New Name");
        assertThat(saved.getRole()).isEqualTo("ADMIN");
        List<ChangeLogEntity> logs = changeLogs.findAll();
        assertThat(logs).extracting(ChangeLogEntity::getFieldName).containsExactlyInAnyOrder("이름", "권한");
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.getChangedBy()).isEqualTo(admin.getId());
            assertThat(log.getTargetName()).isEqualTo("maint001");
        });
        assertThat(logs).filteredOn(log -> log.getFieldName().equals("권한")).singleElement()
                .satisfies(log -> {
                    assertThat(log.getOldValue()).isEqualTo("사용자");
                    assertThat(log.getNewValue()).isEqualTo("관리자");
                });
    }

    @Test
    void the_new_position_applies_to_the_users_existing_token_at_once() throws Exception {
        AccountEntity account = approvedAccount("maint001", "USER");
        String bearer = bearerFor(account);
        mockMvc.perform(get(URL).header("Authorization", bearer)).andExpect(status().isForbidden());

        mockMvc.perform(put(URL + "/" + account.getId()).header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content(body("테스터", "ADMIN")));

        mockMvc.perform(get(URL).header("Authorization", bearer)).andExpect(status().isUnauthorized());
    }

    @Test
    void an_update_without_changes_records_nothing() throws Exception {
        AccountEntity account = approvedAccount("maint001", "USER");

        mockMvc.perform(put(URL + "/" + account.getId()).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("테스터", "USER")))
                .andExpect(status().isOk());

        assertThat(changeLogs.count()).isZero();
    }

    @Test
    void an_invalid_update_returns_400_and_changes_nothing() throws Exception {
        AccountEntity account = approvedAccount("maint001", "USER");
        String[] invalid = {body("", "USER"), body("   ", "USER"), body("가".repeat(101), "USER"),
                body("Name", "BOSS"), "{\"name\":\"Name\"}", "{\"permission\":\"USER\"}", "{not-json"};

        for (String request : invalid) {
            mockMvc.perform(put(URL + "/" + account.getId()).header("Authorization", adminBearer)
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(accounts.findById(account.getId()).orElseThrow().getName()).isEqualTo("테스터");
        assertThat(changeLogs.count()).isZero();
    }

    // ---- 시스템관리자는 최소 한 명 ----

    @Test
    void the_last_system_admin_cannot_be_demoted() throws Exception {
        mockMvc.perform(put(URL + "/" + admin.getId()).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("테스터", "USER")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("LAST_ADMIN_REQUIRED"));

        assertThat(accounts.findById(admin.getId()).orElseThrow().getRole()).isEqualTo("ADMIN");
        assertThat(changeLogs.count()).isZero();
    }

    @Test
    void the_last_system_admin_can_still_change_their_name() throws Exception {
        mockMvc.perform(put(URL + "/" + admin.getId()).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("새이름", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"));
    }

    @Test
    void after_promoting_someone_else_the_former_admin_can_step_down() throws Exception {
        AccountEntity successor = approvedAccount("manager1", "USER");

        mockMvc.perform(put(URL + "/" + successor.getId()).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("후임", "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(put(URL + "/" + admin.getId()).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("테스터", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").doesNotExist());

        // 이제는 후임이 마지막 시스템관리자다.
        mockMvc.perform(put(URL + "/" + successor.getId()).header("Authorization", bearerFor(successor))
                        .contentType(MediaType.APPLICATION_JSON).content(body("후임", "USER")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("LAST_ADMIN_REQUIRED"));
    }

    @Test
    void two_admins_demoting_each_other_at_once_cannot_leave_the_system_without_an_admin() throws Exception {
        AccountEntity other = approvedAccount("admin002", "ADMIN");
        String otherBearer = bearerFor(other);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = pool.submit(() -> {
                start.await();
                return mockMvc.perform(put(URL + "/" + other.getId()).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("테스터", "USER")))
                        .andReturn().getResponse().getStatus();
            });
            var second = pool.submit(() -> {
                start.await();
                return mockMvc.perform(put(URL + "/" + admin.getId()).header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body("테스터", "USER")))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>(List.of(first.get(), second.get()));
            java.util.Collections.sort(statuses);

            // 먼저 처리된 쪽만 성공한다. 나중 요청은 성공 직후의 상태(시스템관리자 1명)를 보고 거절되거나,
            // 강등된 사람이 보낸 요청이면 권한 검사에서 막힌다.
            assertThat(statuses.get(0)).isEqualTo(200);
            // 이미 강등된 계정의 토큰이 폐기되면 인증 단계에서 401로 거절될 수도 있다.
            assertThat(statuses.get(1)).isIn(401, 403, 422);
            assertThat(accounts.findAll()).filteredOn(a -> "ADMIN".equals(a.getRole()) && !a.isDeleted())
                    .as("시스템관리자는 한 명 남는다").hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- USR-004 삭제 ----

    @Test
    void deleting_is_logical_clears_secrets_and_ends_access_at_once() throws Exception {
        AccountEntity account = approvedAccount("maint001", "USER");
        account.connectTelegram("777", LocalDateTime.now(clock));
        accounts.save(account);
        String bearer = bearerFor(account);

        mockMvc.perform(delete(URL + "/" + account.getId()).header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        AccountEntity saved = accounts.findById(account.getId()).orElseThrow();
        assertThat(saved.isDeleted()).as("줄은 남는다").isTrue();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getTelegramChatId()).isNull();
        assertThat(saved.getName()).as("이름은 지난 기록을 위해 남긴다").isEqualTo("테스터");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("maint001", PASSWORD))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(1));
        assertThat(changeLogs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getFieldName()).isEqualTo("계정 삭제");
            assertThat(log.getChangedBy()).as("삭제한 사람은 이력에 남는다").isEqualTo(admin.getId());
        });
        assertThat(events.stream(AccountDeletedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.accountId()).isEqualTo(account.getId());
            assertThat(event.position()).isEqualTo("USER");
            assertThat(event.deletedBy()).isEqualTo(admin.getId());
        });
        mockMvc.perform(delete(URL + "/" + account.getId()).header("Authorization", adminBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void an_admin_cannot_delete_their_own_account() throws Exception {
        mockMvc.perform(delete(URL + "/" + admin.getId()).header("Authorization", adminBearer))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("USER_SELF_DELETE_FORBIDDEN"));

        assertThat(accounts.findById(admin.getId()).orElseThrow().isDeleted()).isFalse();
    }
}

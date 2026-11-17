package com.sfaasproject.stf.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AbstractAuthIntegrationTest;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.entity.ChangeLogEntity;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.entity.ApprovalStatus;
import com.sfaasproject.stf.user.event.SignupDecidedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** APR-002 승인, APR-003 거절. */
@RecordApplicationEvents
class SignupDecisionApiTest extends AbstractAuthIntegrationTest {

    private static final String BASE = "/api/v1/system/signup-requests/";

    @Autowired
    private ApplicationEvents events;

    private AccountEntity admin;
    private String adminBearer;

    @BeforeEach
    void admin() {
        admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        adminBearer = bearerFor(admin);
    }

    private String approvalUrl(long id) {
        return BASE + id + "/approval";
    }

    private String rejectionUrl(long id) {
        return BASE + id + "/rejection";
    }

    private static String position(String position) {
        return "{\"position\":\"" + position + "\"}";
    }

    private static String reason(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    private List<SignupDecidedEvent> decisions() {
        return events.stream(SignupDecidedEvent.class).toList();
    }

    // ---- APR-002 승인 ----

    @Test
    void approving_activates_the_account_with_the_chosen_position() throws Exception {
        AccountEntity request = pendingAccount("newuser1");

        mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(position("LINE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(request.getId()))
                .andExpect(jsonPath("$.data.loginId").value("newuser1"))
                .andExpect(jsonPath("$.data.position").value("LINE_MANAGER"))
                .andExpect(jsonPath("$.data.permission").value("GENERAL"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.telegramConnected").value(false))
                .andExpect(jsonPath("$.data.lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.updatedAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        AccountEntity saved = accounts.findById(request.getId()).orElseThrow();
        assertThat(saved.getRole()).isEqualTo("LINE_MANAGER");
        assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(saved.getApprovedBy()).isEqualTo(admin.getId());
        assertThat(saved.getApprovedAt()).isNotNull();
    }

    @Test
    void the_system_admin_position_maps_to_the_admin_permission() throws Exception {
        AccountEntity request = pendingAccount("newadmin");

        mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(position("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data.permission").value("ADMIN"));
    }

    @Test
    void an_approved_applicant_can_log_in() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("newuser1", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_APPROVAL_PENDING"));

        mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("newuser1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value("MAINTENANCE"));
    }

    @Test
    void approval_is_recorded_in_the_change_log_without_secrets() throws Exception {
        AccountEntity request = pendingAccount("newuser1");

        mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")));

        List<ChangeLogEntity> logs = changeLogs.findAll();
        assertThat(logs).hasSize(1);
        ChangeLogEntity log = logs.get(0);
        assertThat(log.getChangedBy()).isEqualTo(admin.getId());
        assertThat(log.getTargetType()).isEqualTo(AuditTargetType.ACCOUNT);
        assertThat(log.getTargetName()).isEqualTo("newuser1");
        assertThat(log.getFieldName()).isEqualTo("가입 승인");
        assertThat(log.getOldValue()).isEqualTo("승인 대기");
        assertThat(log.getNewValue()).contains("보전 담당자");
        assertThat(String.join(" ", log.getTargetName(), log.getFieldName(), log.getOldValue(),
                log.getNewValue())).doesNotContain(PASSWORD).doesNotContain("$2");
    }

    @Test
    void approving_publishes_a_decision_event_with_the_telegram_chat_when_connected() throws Exception {
        AccountEntity connected = pendingAccount("linked001");
        connected.connectTelegram("123456789", LocalDateTime.now(clock));
        accounts.save(connected);
        AccountEntity unlinked = pendingAccount("unlinked1");

        mockMvc.perform(post(approvalUrl(connected.getId())).header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")));
        mockMvc.perform(post(approvalUrl(unlinked.getId())).header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")));

        assertThat(decisions()).hasSize(2);
        SignupDecidedEvent first = decisions().get(0);
        assertThat(first.outcome()).isEqualTo(SignupDecidedEvent.Outcome.APPROVED);
        assertThat(first.accountId()).isEqualTo(connected.getId());
        assertThat(first.position()).isEqualTo("MAINTENANCE");
        assertThat(first.telegramChatId()).isEqualTo("123456789");
        assertThat(first.reason()).isNull();
        assertThat(decisions().get(1).telegramChatId()).isNull();
        assertThat(first.toString()).doesNotContain("123456789");
    }

    @Test
    void approving_twice_returns_409_and_changes_nothing_more() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")));

        mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(position("SYSTEM_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SIGNUP_REQUEST_STATE_CONFLICT"));

        assertThat(accounts.findById(request.getId()).orElseThrow().getRole()).isEqualTo("MAINTENANCE");
        assertThat(changeLogs.count()).isEqualTo(1);
        assertThat(decisions()).hasSize(1);
    }

    @Test
    void an_unknown_or_deleted_request_returns_404() throws Exception {
        AccountEntity deleted = pendingAccount("deleted01");
        deleted.markDeleted(LocalDateTime.now(clock));
        accounts.save(deleted);

        for (long id : new long[] {987654321L, deleted.getId()}) {
            mockMvc.perform(post(approvalUrl(id)).header("Authorization", adminBearer)
                            .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }
        assertThat(changeLogs.count()).isZero();
    }

    @Test
    void an_invalid_approval_body_returns_400() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        String[] invalid = {"{}", position("BOSS"), position("ADMIN"), "{\"position\":null}", "{not-json"};

        for (String body : invalid) {
            mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", adminBearer)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(post(BASE + "abc/approval").header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")))
                .andExpect(status().isBadRequest());
        assertThat(accounts.findById(request.getId()).orElseThrow().isPending()).isTrue();
    }

    @Test
    void only_the_system_admin_can_approve() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        String manager = bearerFor(approvedAccount("manager1", "LINE_MANAGER"));

        mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_PERMISSION_DENIED"));
        mockMvc.perform(post(approvalUrl(request.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")))
                .andExpect(status().isUnauthorized());

        assertThat(accounts.findById(request.getId()).orElseThrow().isPending()).isTrue();
    }

    // ---- APR-003 거절 ----

    @Test
    void rejecting_deletes_the_request_and_the_login_id_can_be_used_again() throws Exception {
        AccountEntity request = pendingAccount("newuser1");

        mockMvc.perform(post(rejectionUrl(request.getId())).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(reason("담당 부서가 아닙니다")))
                .andExpect(status().isNoContent());

        assertThat(accounts.findById(request.getId())).isEmpty();
        mockMvc.perform(post("/api/v1/auth/signups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"newuser1\",\"password\":\"" + PASSWORD + "\",\"name\":\"Again\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejection_is_recorded_with_the_reason_and_published_as_an_event() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        request.connectTelegram("555", LocalDateTime.now(clock));
        accounts.save(request);

        mockMvc.perform(post(rejectionUrl(request.getId())).header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content(reason("담당 부서가 아닙니다")));

        List<ChangeLogEntity> logs = changeLogs.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getChangedBy()).isEqualTo(admin.getId());
        assertThat(logs.get(0).getTargetType()).isEqualTo(AuditTargetType.ACCOUNT);
        assertThat(logs.get(0).getTargetName()).isEqualTo("newuser1");
        assertThat(logs.get(0).getNewValue()).contains("거절").contains("담당 부서가 아닙니다");
        SignupDecidedEvent event = decisions().get(0);
        assertThat(event.outcome()).isEqualTo(SignupDecidedEvent.Outcome.REJECTED);
        assertThat(event.accountId()).isEqualTo(request.getId());
        assertThat(event.telegramChatId()).as("계정이 삭제되므로 알릴 대상은 이벤트에 담긴다").isEqualTo("555");
        assertThat(event.reason()).isEqualTo("담당 부서가 아닙니다");
    }

    @Test
    void a_very_long_reason_is_cut_to_fit_the_change_log_column() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        String longest = "가".repeat(500);

        mockMvc.perform(post(rejectionUrl(request.getId())).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(reason(longest)))
                .andExpect(status().isNoContent());

        assertThat(changeLogs.findAll().get(0).getNewValue()).hasSize(ChangeLogEntity.VALUE_MAX);
    }

    @Test
    void rejecting_twice_or_after_approval_is_refused() throws Exception {
        AccountEntity rejected = pendingAccount("rejected1");
        AccountEntity approved = pendingAccount("approved1");
        mockMvc.perform(post(rejectionUrl(rejected.getId())).header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content(reason("no")));
        mockMvc.perform(post(approvalUrl(approved.getId())).header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")));

        mockMvc.perform(post(rejectionUrl(rejected.getId())).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(reason("again")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(rejectionUrl(approved.getId())).header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(reason("too late")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SIGNUP_REQUEST_STATE_CONFLICT"));

        assertThat(accounts.findById(approved.getId())).as("승인된 계정은 지워지지 않는다").isPresent();
    }

    @Test
    void an_invalid_rejection_body_returns_400_and_keeps_the_request() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        String[] invalid = {"{}", reason(""), reason("   "), reason("가".repeat(501)), "{\"reason\":null}", "{not-json"};

        for (String body : invalid) {
            mockMvc.perform(post(rejectionUrl(request.getId())).header("Authorization", adminBearer)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(accounts.findById(request.getId())).isPresent();
        assertThat(changeLogs.count()).isZero();
    }

    @Test
    void only_the_system_admin_can_reject() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        String maintainer = bearerFor(approvedAccount("maint001", "MAINTENANCE"));

        mockMvc.perform(post(rejectionUrl(request.getId())).header("Authorization", maintainer)
                        .contentType(MediaType.APPLICATION_JSON).content(reason("no")))
                .andExpect(status().isForbidden());

        assertThat(accounts.findById(request.getId())).isPresent();
    }

    // ---- 동시 처리 ----

    @Test
    void approving_and_rejecting_the_same_request_at_once_lets_only_one_win() throws Exception {
        AccountEntity request = pendingAccount("newuser1");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> approval = pool.submit(() -> {
                start.await();
                return mockMvc.perform(post(approvalUrl(request.getId())).header("Authorization", adminBearer)
                                .contentType(MediaType.APPLICATION_JSON).content(position("MAINTENANCE")))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> rejection = pool.submit(() -> {
                start.await();
                return mockMvc.perform(post(rejectionUrl(request.getId())).header("Authorization", adminBearer)
                                .contentType(MediaType.APPLICATION_JSON).content(reason("no")))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            int approvalStatus = approval.get();
            int rejectionStatus = rejection.get();

            boolean approvalWon = approvalStatus == 200 && (rejectionStatus == 409 || rejectionStatus == 404);
            boolean rejectionWon = rejectionStatus == 204 && (approvalStatus == 404 || approvalStatus == 409);
            assertThat(approvalWon || rejectionWon)
                    .as("approval=%d, rejection=%d", approvalStatus, rejectionStatus).isTrue();
            assertThat(changeLogs.count()).as("이력은 이긴 쪽만 남는다").isEqualTo(1);
            assertThat(decisions()).hasSize(1);
            assertThat(accounts.findById(request.getId()).isPresent()).isEqualTo(approvalWon);
        } finally {
            pool.shutdownNow();
        }
    }
}

package com.sfaasproject.stf.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.user.entity.AccountEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Bearer 토큰 검증과 직책별 접근 제어. 아직 만들지 않은 경로(404)는 "인증과 권한을 통과했다"는 뜻으로 쓴다.
 * 권한은 토큰이 아니라 DB 의 현재 직책으로 정해지므로 모든 토큰은 실제 계정으로 만든다.
 */
class AuthSecurityTest extends AbstractAuthIntegrationTest {

    private static final String PROTECTED = "/api/v1/equipment/1";
    private static final String SYSTEM_ONLY = "/api/v1/system/settings/judgement";
    private static final String DUTY_LIST = "/api/v1/system/alarm-duty-schedules";
    private static final String DUTY_CURRENT = "/api/v1/system/alarm-duty-schedules/current";

    private JwtAccessTokenService.Issued tokenOf(AccountEntity account, Position tokenPosition) {
        return jwt.issue(account.getId(), tokenPosition, clock.instant());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    // ---- 토큰 자체 ----

    @Test
    void a_protected_api_without_a_token_returns_401() throws Exception {
        mockMvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void a_garbage_token_returns_401() throws Exception {
        mockMvc.perform(get(PROTECTED).header("Authorization", bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void a_token_signed_with_another_key_returns_401() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        JwtAccessTokenService other = new JwtAccessTokenService(
                "another-secret-key-another-secret-key-1234".getBytes(), "stf");
        String forged = other.issue(admin.getId(), Position.SYSTEM_ADMIN, clock.instant()).value();

        mockMvc.perform(get(SYSTEM_ONLY).header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void an_expired_token_returns_401() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        String expired = jwt.issue(admin.getId(), Position.SYSTEM_ADMIN,
                clock.instant().minus(Duration.ofHours(2))).value();

        mockMvc.perform(get(PROTECTED).header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void a_valid_token_passes_authentication() throws Exception {
        AccountEntity maintainer = approvedAccount("maint001", "MAINTENANCE");

        mockMvc.perform(get(PROTECTED).header("Authorization", bearerFor(maintainer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void the_bearer_scheme_is_case_insensitive() throws Exception {
        AccountEntity maintainer = approvedAccount("maint001", "MAINTENANCE");

        mockMvc.perform(get(PROTECTED).header("Authorization",
                        "bearer " + tokenOf(maintainer, Position.MAINTENANCE).value()))
                .andExpect(status().isNotFound());
    }

    // ---- 직책별 접근 ----

    @Test
    void system_apis_are_only_open_to_the_system_admin() throws Exception {
        String admin = bearerFor(approvedAccount("admin001", "SYSTEM_ADMIN"));
        String manager = bearerFor(approvedAccount("manager1", "LINE_MANAGER"));
        String maintainer = bearerFor(approvedAccount("maint001", "MAINTENANCE"));

        mockMvc.perform(get(SYSTEM_ONLY).header("Authorization", admin)).andExpect(status().isNotFound());
        for (String bearer : new String[] {manager, maintainer}) {
            mockMvc.perform(get(SYSTEM_ONLY).header("Authorization", bearer))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("AUTH_PERMISSION_DENIED"));
        }
    }

    @Test
    void the_duty_schedule_can_be_read_by_every_position_but_changed_only_by_the_system_admin()
            throws Exception {
        String admin = bearerFor(approvedAccount("admin001", "SYSTEM_ADMIN"));
        String manager = bearerFor(approvedAccount("manager1", "LINE_MANAGER"));
        String maintainer = bearerFor(approvedAccount("maint001", "MAINTENANCE"));

        for (String bearer : new String[] {admin, manager, maintainer}) {
            for (String readPath : new String[] {DUTY_LIST, DUTY_CURRENT}) {
                assertThat(statusOf(get(readPath).header("Authorization", bearer)))
                        .as("GET %s", readPath).isNotIn(401, 403);
            }
        }
        for (String bearer : new String[] {manager, maintainer}) {
            assertThat(statusOf(post(DUTY_LIST).header("Authorization", bearer))).isEqualTo(403);
            assertThat(statusOf(put(DUTY_LIST + "/1").header("Authorization", bearer))).isEqualTo(403);
            assertThat(statusOf(delete(DUTY_LIST + "/1").header("Authorization", bearer))).isEqualTo(403);
        }
        assertThat(statusOf(post(DUTY_LIST).header("Authorization", admin))).isNotIn(401, 403);
        assertThat(statusOf(get(DUTY_LIST))).as("토큰이 없으면 401").isEqualTo(401);
    }

    @Test
    void the_current_position_in_the_database_decides_not_the_one_inside_the_token() throws Exception {
        AccountEntity promoted = approvedAccount("promoted", "SYSTEM_ADMIN");
        AccountEntity demoted = approvedAccount("demoted1", "LINE_MANAGER");
        // 토큰은 이전 직책으로 발급되었다.
        String staleLowToken = bearer(tokenOf(promoted, Position.MAINTENANCE).value());
        String staleHighToken = bearer(tokenOf(demoted, Position.SYSTEM_ADMIN).value());

        mockMvc.perform(get(SYSTEM_ONLY).header("Authorization", staleLowToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(SYSTEM_ONLY).header("Authorization", staleHighToken))
                .andExpect(status().isForbidden());
    }

    // ---- 계정 상태 ----

    @Test
    void a_token_of_a_deleted_account_is_rejected_at_once() throws Exception {
        AccountEntity account = approvedAccount("gone0001", "SYSTEM_ADMIN");
        String bearer = bearerFor(account);
        mockMvc.perform(get(PROTECTED).header("Authorization", bearer)).andExpect(status().isNotFound());

        account.markDeleted(LocalDateTime.now(clock));
        accounts.save(account);

        mockMvc.perform(get(PROTECTED).header("Authorization", bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void a_token_for_an_account_that_is_not_approved_or_does_not_exist_is_rejected() throws Exception {
        AccountEntity pending = pendingAccount("waiting1");
        String forPending = bearer(tokenOf(pending, Position.SYSTEM_ADMIN).value());
        String forMissing = bearer(jwt.issue(987654321L, Position.SYSTEM_ADMIN, clock.instant()).value());

        mockMvc.perform(get(PROTECTED).header("Authorization", forPending)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROTECTED).header("Authorization", forMissing)).andExpect(status().isUnauthorized());
    }

    @Test
    void an_account_that_must_change_its_password_can_still_use_its_token() throws Exception {
        AccountEntity account = approvedAccount("worker01", "MAINTENANCE");
        account.requirePasswordChange(LocalDateTime.now(clock));
        accounts.save(account);

        mockMvc.perform(get(PROTECTED).header("Authorization", bearerFor(account)))
                .andExpect(status().isNotFound());
    }

    // ---- 폐기 ----

    @Test
    void a_revoked_access_token_is_rejected() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        JwtAccessTokenService.Issued issued = tokenOf(admin, Position.SYSTEM_ADMIN);
        revocations.revokeAccess(issued.jti(), issued.expiresAt());

        mockMvc.perform(get(PROTECTED).header("Authorization", bearer(issued.value())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void tokens_issued_before_a_users_cutoff_are_rejected_but_other_users_are_not() throws Exception {
        AccountEntity mine = approvedAccount("admin001", "SYSTEM_ADMIN");
        AccountEntity others = approvedAccount("admin002", "SYSTEM_ADMIN");
        String mineToken = bearerFor(mine);
        String othersToken = bearerFor(others);
        revocations.revokeUserTokensIssuedUntil(mine.getId(), Instant.now().plusSeconds(5));

        mockMvc.perform(get(PROTECTED).header("Authorization", mineToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROTECTED).header("Authorization", othersToken)).andExpect(status().isNotFound());
    }

    @Test
    void tokens_are_rejected_when_the_revocation_store_is_unavailable() throws Exception {
        String token = bearerFor(approvedAccount("admin001", "SYSTEM_ADMIN"));
        revocations.setFailing(true);

        mockMvc.perform(get(PROTECTED).header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    // ---- 공개 경로 ----

    @Test
    void swagger_docs_and_health_are_public() throws Exception {
        for (String path : new String[] {"/v3/api-docs", "/actuator/health"}) {
            assertThat(statusOf(get(path))).as(path).isNotIn(401, 403);
        }
    }

    @Test
    void the_old_form_login_page_is_gone() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void only_login_signup_and_refresh_are_public_auth_endpoints() throws Exception {
        // 갱신은 토큰 없이 호출하는 API 라서 보안 필터가 아니라 갱신 자체의 오류 코드로 거절된다.
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID"));
        for (var request : new MockHttpServletRequestBuilder[] {post("/api/v1/auth/logout"),
                get("/api/v1/auth/me"), put("/api/v1/auth/me/password")}) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
        }
    }
}

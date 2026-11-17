package com.sfaasproject.stf.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.entity.AuthTokenEntity;
import com.sfaasproject.stf.auth.service.RefreshTokenService;
import com.sfaasproject.stf.user.entity.AccountEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** AUTH-001 로그인. */
@ExtendWith(OutputCaptureExtension.class)
class AuthLoginApiTest extends AbstractAuthIntegrationTest {

    private static final String URL = "/api/v1/auth/login";

    private MvcResult login(String loginId, String password) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(loginId, password))).andReturn();
    }

    private String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull().startsWith("refreshToken=");
        return header.substring("refreshToken=".length(), header.indexOf(';'));
    }

    @Test
    void login_succeeds_and_returns_the_user_an_access_token_and_a_refresh_cookie() throws Exception {
        AccountEntity admin = approvedAccount("admin01", "SYSTEM_ADMIN");

        MvcResult result = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin01", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(admin.getId()))
                .andExpect(jsonPath("$.data.loginId").value("admin01"))
                .andExpect(jsonPath("$.data.name").value("테스터"))
                .andExpect(jsonPath("$.data.position").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data.permission").value("ADMIN"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.lastLoginAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        String accessToken = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.accessToken");
        assertThat(accessToken.split("\\.")).as("JWT 는 점 두 개로 나뉜 세 부분이다").hasSize(3);
        assertThat(jwt.verify(accessToken, clock.instant()).userId()).isEqualTo(admin.getId());
    }

    @Test
    void the_refresh_token_is_sent_only_as_a_hardened_cookie_and_stored_only_as_a_hash() throws Exception {
        AccountEntity admin = approvedAccount("admin01", "SYSTEM_ADMIN");

        MvcResult result = login("admin01", PASSWORD);

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth",
                "Max-Age=604800");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");

        String rawRefresh = cookieValue(result);
        List<AuthTokenEntity> rows = tokens.findAll();
        assertThat(rows).hasSize(1);
        AuthTokenEntity row = rows.get(0);
        assertThat(row.getAccountId()).isEqualTo(admin.getId());
        assertThat(row.getTokenHash()).hasSize(64)
                .isNotEqualTo(rawRefresh)
                .isEqualTo(new RefreshTokenService().hash(rawRefresh));
        assertThat(row.getFamilyId()).isNotBlank();
        assertThat(row.getUsedAt()).isNull();
        assertThat(row.getRevokedAt()).isNull();
        assertThat(row.getExpiresAt()).isAfter(LocalDateTime.now(clock).plusDays(6));
    }

    @Test
    void a_successful_login_records_the_last_login_time() throws Exception {
        AccountEntity admin = approvedAccount("admin01", "SYSTEM_ADMIN");
        assertThat(admin.getLastLoginAt()).isNull();

        login("admin01", PASSWORD);

        assertThat(accounts.findById(admin.getId()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void every_login_starts_its_own_token_family() throws Exception {
        approvedAccount("admin01", "SYSTEM_ADMIN");

        login("admin01", PASSWORD);
        login("admin01", PASSWORD);

        assertThat(tokens.findAll()).extracting(AuthTokenEntity::getFamilyId).doesNotHaveDuplicates()
                .hasSize(2);
    }

    @Test
    void a_line_manager_gets_the_general_permission() throws Exception {
        approvedAccount("manager1", "LINE_MANAGER");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("manager1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value("LINE_MANAGER"))
                .andExpect(jsonPath("$.data.permission").value("GENERAL"));
    }

    @Test
    void an_account_that_must_change_its_password_can_still_log_in() throws Exception {
        AccountEntity account = approvedAccount("worker01", "MAINTENANCE");
        account.requirePasswordChange(LocalDateTime.now(clock));
        accounts.save(account);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("worker01", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void a_wrong_password_returns_401_and_changes_nothing() throws Exception {
        AccountEntity admin = approvedAccount("admin01", "SYSTEM_ADMIN");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin01", "Wrong0ne!x")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));

        assertThat(tokens.count()).isZero();
        assertThat(accounts.findById(admin.getId()).orElseThrow().getLastLoginAt())
                .as("실패하면 최종 로그인 시각을 갱신하지 않는다").isNull();
    }

    @Test
    void an_unknown_login_id_returns_the_same_401_as_a_wrong_password() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody01", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void a_pending_account_gets_403_only_when_the_password_is_right() throws Exception {
        pendingAccount("waiting1");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("waiting1", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_APPROVAL_PENDING"));
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("waiting1", "Wrong0ne!x")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        assertThat(tokens.count()).isZero();
    }

    @Test
    void a_deleted_account_cannot_log_in() throws Exception {
        AccountEntity account = approvedAccount("gone0001", "MAINTENANCE");
        account.markDeleted(LocalDateTime.now(clock));
        accounts.save(account);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("gone0001", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void malformed_input_returns_400() throws Exception {
        approvedAccount("admin01", "SYSTEM_ADMIN");
        String[] invalidBodies = {
                loginBody("ADMIN01", PASSWORD),   // 대문자 아이디
                loginBody("ad", PASSWORD),        // 너무 짧은 아이디
                loginBody("admin01", "short1!"),  // 비밀번호 정책 위반
                loginBody("admin01", ""),         // 빈 비밀번호
                loginBody("", PASSWORD),          // 빈 아이디
                "{}",
                "{not-json"
        };
        for (String invalid : invalidBodies) {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void a_stale_authorization_header_does_not_block_logging_in_again() throws Exception {
        approvedAccount("admin01", "SYSTEM_ADMIN");

        mockMvc.perform(post(URL).header("Authorization", "Bearer expired.or.garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin01", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void passwords_and_tokens_never_appear_in_the_logs(CapturedOutput output) throws Exception {
        approvedAccount("admin01", "SYSTEM_ADMIN");

        MvcResult ok = login("admin01", PASSWORD);
        login("admin01", "Wrong0ne!x");
        String accessToken = com.jayway.jsonpath.JsonPath.read(ok.getResponse().getContentAsString(),
                "$.data.accessToken");
        String refreshToken = cookieValue(ok);

        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain("Wrong0ne!x")
                .doesNotContain(accessToken)
                .doesNotContain(refreshToken);
    }
}

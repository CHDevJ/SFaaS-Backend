package com.sfaasproject.stf.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.user.entity.AccountEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** AUTH-003 로그아웃, AUTH-004 내 정보, AUTH-006 비밀번호 변경. */
@ExtendWith(OutputCaptureExtension.class)
class AuthSessionApiTest extends AbstractSessionApiTest {

    private String passwordBody(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    /** 비밀번호 변경 직후 같은 초에 다시 로그인하면 폐기 기준(초 단위)에 걸리므로 잠시 기다린다. */
    private static void waitForTheNextSecond() throws InterruptedException {
        Thread.sleep(1100);
    }

    // ---- AUTH-004 내 정보 ----

    @Test
    void me_returns_the_logged_in_user_without_secrets() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");

        mockMvc.perform(get(ME).header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(admin.getId()))
                .andExpect(jsonPath("$.data.loginId").value("admin001"))
                .andExpect(jsonPath("$.data.name").value("테스터"))
                .andExpect(jsonPath("$.data.position").doesNotExist())
                .andExpect(jsonPath("$.data.permission").value("ADMIN"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.lastLoginAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void me_shows_a_null_last_login_for_an_account_that_never_logged_in() throws Exception {
        AccountEntity manager = approvedAccount("manager1", "USER");

        mockMvc.perform(get(ME).header("Authorization", bearerFor(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(hasKey("lastLoginAt")))
                .andExpect(jsonPath("$.data.lastLoginAt").value(nullValue()))
                .andExpect(jsonPath("$.data.permission").value("USER"));
    }

    @Test
    void me_reports_the_password_change_required_status() throws Exception {
        AccountEntity account = approvedAccount("worker01", "USER");
        account.requirePasswordChange(LocalDateTime.now(clock));
        accounts.save(account);

        mockMvc.perform(get(ME).header("Authorization", bearerFor(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void me_requires_a_token() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    // ---- AUTH-003 로그아웃 ----

    @Test
    void logout_revokes_the_access_token_and_the_refresh_family_and_clears_the_cookie() throws Exception {
        approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");

        MvcResult result = mockMvc.perform(withRefresh(post(LOGOUT), session.refresh())
                        .header("Authorization", session.bearer()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertRefreshCookieCleared(result);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        mockMvc.perform(get(ME).header("Authorization", session.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(withRefresh(post(REFRESH), session.refresh()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID"));
        assertThat(tokens.findAll()).allSatisfy(row -> assertThat(row.getRevokedAt()).isNotNull());
    }

    @Test
    void logout_without_the_refresh_cookie_still_revokes_the_access_token() throws Exception {
        approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");

        mockMvc.perform(post(LOGOUT).header("Authorization", session.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ME).header("Authorization", session.bearer()))
                .andExpect(status().isUnauthorized());
        assertThat(tokens.findAll()).as("쿠키가 없으면 Refresh 계열은 건드리지 않는다")
                .allSatisfy(row -> assertThat(row.getRevokedAt()).isNull());
    }

    @Test
    void logout_only_ends_the_current_session_not_other_logins_of_the_same_user() throws Exception {
        approvedAccount("admin001", "ADMIN");
        Session first = login("admin001");
        Session second = login("admin001");

        mockMvc.perform(withRefresh(post(LOGOUT), first.refresh()).header("Authorization", first.bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ME).header("Authorization", second.bearer())).andExpect(status().isOk());
        mockMvc.perform(withRefresh(post(REFRESH), second.refresh())).andExpect(status().isOk());
    }

    @Test
    void logout_needs_a_valid_token_and_a_second_logout_with_the_same_token_is_refused() throws Exception {
        approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");

        mockMvc.perform(post(LOGOUT)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(LOGOUT).header("Authorization", session.bearer())).andExpect(status().isNoContent());
        mockMvc.perform(post(LOGOUT).header("Authorization", session.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    // ---- AUTH-006 비밀번호 변경 ----

    @Test
    void changing_the_password_revokes_every_token_and_requires_logging_in_again() throws Exception {
        approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");
        Session otherDevice = login("admin001");

        MvcResult result = mockMvc.perform(put(PASSWORD_URL).header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertRefreshCookieCleared(result);
        for (Session revoked : new Session[] {session, otherDevice}) {
            mockMvc.perform(get(ME).header("Authorization", revoked.bearer()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(withRefresh(post(REFRESH), revoked.refresh()))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody("admin001", PASSWORD)))
                .andExpect(status().isUnauthorized());

        waitForTheNextSecond();
        Session fresh = login("admin001", NEW_PASSWORD);
        mockMvc.perform(get(ME).header("Authorization", fresh.bearer())).andExpect(status().isOk());
    }

    @Test
    void a_wrong_current_password_returns_401_and_changes_nothing() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");
        String hashBefore = accounts.findById(admin.getId()).orElseThrow().getPasswordHash();

        mockMvc.perform(put(PASSWORD_URL).header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(passwordBody("Wrong0ne!x", NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));

        assertThat(accounts.findById(admin.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashBefore);
        mockMvc.perform(get(ME).header("Authorization", session.bearer())).andExpect(status().isOk());
    }

    @Test
    void a_new_password_that_breaks_the_policy_returns_400_and_changes_nothing() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");
        String hashBefore = accounts.findById(admin.getId()).orElseThrow().getPasswordHash();

        for (String weak : new String[] {"short1!", "NoSpecial123", "no-digits-here!", "12345678!!", "a".repeat(65) + "1!"}) {
            mockMvc.perform(put(PASSWORD_URL).header("Authorization", session.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, weak)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("PASSWORD_POLICY_VIOLATION"));
        }
        assertThat(accounts.findById(admin.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashBefore);
        mockMvc.perform(get(ME).header("Authorization", session.bearer())).andExpect(status().isOk());
    }

    @Test
    void missing_or_blank_fields_return_400() throws Exception {
        approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");

        for (String body : new String[] {"{}", passwordBody("", NEW_PASSWORD), passwordBody(PASSWORD, ""),
                "{\"currentPassword\":\"" + PASSWORD + "\"}", "{not-json"}) {
            mockMvc.perform(put(PASSWORD_URL).header("Authorization", session.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void changing_the_password_clears_the_password_change_required_state() throws Exception {
        AccountEntity account = approvedAccount("worker01", "USER");
        account.requirePasswordChange(LocalDateTime.now(clock));
        accounts.save(account);
        Session session = login("worker01");
        mockMvc.perform(get(ME).header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.accountStatus").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(put(PASSWORD_URL).header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(accounts.findById(account.getId()).orElseThrow().isMustChangePassword()).isFalse();
        waitForTheNextSecond();
        mockMvc.perform(get(ME).header("Authorization", login("worker01", NEW_PASSWORD).bearer()))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
    }

    @Test
    void other_users_are_not_affected_by_a_password_change() throws Exception {
        approvedAccount("admin001", "ADMIN");
        approvedAccount("admin002", "ADMIN");
        Session changer = login("admin001");
        Session bystander = login("admin002");

        mockMvc.perform(put(PASSWORD_URL).header("Authorization", changer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ME).header("Authorization", bystander.bearer())).andExpect(status().isOk());
        mockMvc.perform(withRefresh(post(REFRESH), bystander.refresh())).andExpect(status().isOk());
    }

    @Test
    void changing_the_password_needs_a_token() throws Exception {
        mockMvc.perform(put(PASSWORD_URL).contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwords_and_tokens_of_these_flows_never_appear_in_the_logs(CapturedOutput output) throws Exception {
        approvedAccount("admin001", "ADMIN");
        Session session = login("admin001");
        MvcResult refreshed = mockMvc.perform(withRefresh(post(REFRESH), session.refresh())).andReturn();
        String rotated = refreshCookieValue(refreshed);
        mockMvc.perform(put(PASSWORD_URL).header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody("Wrong0ne!x", NEW_PASSWORD)));
        mockMvc.perform(put(PASSWORD_URL).header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD, NEW_PASSWORD)));

        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain("Wrong0ne!x")
                .doesNotContain(session.access())
                .doesNotContain(session.refresh())
                .doesNotContain(rotated);
    }
}

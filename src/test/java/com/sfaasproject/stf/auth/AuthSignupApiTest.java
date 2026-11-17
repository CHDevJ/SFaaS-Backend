package com.sfaasproject.stf.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.entity.ApprovalStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** AUTH-005 회원가입 신청. */
class AuthSignupApiTest extends AbstractAuthIntegrationTest {

    private static final String URL = "/api/v1/auth/signups";

    private String body(String loginId, String password, String name) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\",\"name\":\"" + name + "\"}";
    }

    @Test
    void signup_creates_a_pending_account_and_returns_a_telegram_link() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("newuser1", PASSWORD, "홍길동")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").isNumber())
                .andExpect(jsonPath("$.data.loginId").value("newuser1"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.accountStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.requestedAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.telegramLink.linkUrl")
                        .value(org.hamcrest.Matchers.startsWith("https://t.me/stf_test_bot?start=")))
                .andExpect(jsonPath("$.data.telegramLink.qrPayload")
                        .value(org.hamcrest.Matchers.startsWith("https://t.me/stf_test_bot?start=")))
                .andExpect(jsonPath("$.data.telegramLink.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        AccountEntity saved = accounts.findByLoginId("newuser1").orElseThrow();
        assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(saved.getRole()).as("직책은 승인할 때 정한다").isNull();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getLastLoginAt()).isNull();
        assertThat(saved.isDeleted()).isFalse();
        verify(telegramLinkTokenStore).save(org.mockito.ArgumentMatchers.eq(saved.getId()), any(), any());
    }

    @Test
    void signup_trims_the_name() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("newuser1", PASSWORD, "  홍길동  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("홍길동"));
    }

    @Test
    void duplicate_login_id_returns_409_and_creates_nothing() throws Exception {
        approvedAccount("newuser1", "MAINTENANCE");

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("newuser1", PASSWORD, "홍길동")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_LOGIN_ID_DUPLICATE"));

        assertThat(accounts.count()).isEqualTo(1);
        verify(telegramLinkTokenStore, never()).save(anyLong(), any(), any());
    }

    @Test
    void login_id_of_a_deleted_account_cannot_be_reused() throws Exception {
        AccountEntity account = approvedAccount("newuser1", "MAINTENANCE");
        account.markDeleted(LocalDateTime.now(clock));
        accounts.save(account);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("newuser1", PASSWORD, "홍길동")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_LOGIN_ID_DUPLICATE"));
    }

    @Test
    void invalid_input_returns_400_and_creates_nothing() throws Exception {
        String[] invalidBodies = {
                body("NewUser1", PASSWORD, "홍길동"),            // 대문자
                body("abc", PASSWORD, "홍길동"),                 // 4자 미만
                body("a".repeat(21), PASSWORD, "홍길동"),        // 20자 초과
                body("new-user", PASSWORD, "홍길동"),            // 허용하지 않는 문자
                body("newuser1", "Passw0rdx", "홍길동"),         // 특수문자 없음
                body("newuser1", "Pass1!", "홍길동"),            // 8자 미만
                body("newuser1", "password!!", "홍길동"),        // 숫자 없음
                body("newuser1", PASSWORD, ""),                 // 이름 비어 있음
                body("newuser1", PASSWORD, "   "),              // 이름이 공백뿐
                body("newuser1", PASSWORD, "가".repeat(51)),     // ERD 이름 길이(50) 초과
                "{\"loginId\":\"newuser1\",\"password\":\"" + PASSWORD + "\"}", // 이름 없음
                "{}",
                "{not-json"
        };
        for (String invalid : invalidBodies) {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(accounts.count()).isZero();
        verify(telegramLinkTokenStore, never()).save(anyLong(), any(), any());
    }

    @Test
    void a_failure_while_saving_the_link_token_rolls_back_the_account() throws Exception {
        doThrow(new IllegalStateException("redis is down")).when(telegramLinkTokenStore)
                .save(anyLong(), any(), any());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(body("newuser1", PASSWORD, "홍길동")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());

        assertThat(accounts.count()).as("계정은 만들어지지 않아야 한다").isZero();
    }

    @Test
    void signup_does_not_need_a_token_and_ignores_a_stale_authorization_header() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", "Bearer expired.or.garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("newuser1", PASSWORD, "홍길동")))
                .andExpect(status().isCreated());
    }
}

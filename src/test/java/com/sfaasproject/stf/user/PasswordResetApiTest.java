package com.sfaasproject.stf.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AbstractSessionApiTest;
import com.sfaasproject.stf.user.entity.AccountEntity;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

/** USR-005 관리자 비밀번호 초기화. */
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetApiTest extends AbstractSessionApiTest {

    private static final Pattern TEMP = Pattern.compile("임시 비밀번호: (\\S{12})");

    private String adminBearer;
    private AccountEntity target;

    @BeforeEach
    void setUp() {
        adminBearer = bearerFor(approvedAccount("admin001", "ADMIN"));
        target = approvedAccount("maint001", "USER");
        target.connectTelegram("555000", LocalDateTime.now(clock));
        target = accounts.save(target);
    }

    private String url(long id) {
        return "/api/v1/system/users/" + id + "/password-reset";
    }

    /** 보낸 메시지에서 임시 비밀번호를 꺼낸다(사용자가 텔레그램에서 보는 값). */
    private String sentTemporaryPassword() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(temporaryPasswordSender).send(eq("555000"), message.capture());
        Matcher matcher = TEMP.matcher(message.getValue());
        assertThat(matcher.find()).as("메시지에 임시 비밀번호가 있어야 한다").isTrue();
        return matcher.group(1);
    }

    @Test
    void a_reset_sends_a_temporary_password_and_forces_a_change_after_logging_in() throws Exception {
        Session before = login("maint001");
        when(temporaryPasswordSender.send(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post(url(target.getId())).header("Authorization", adminBearer))
                .andExpect(status().isNoContent());

        String temporary = sentTemporaryPassword();
        assertThat(temporary).hasSize(12).matches(".*[A-Za-z].*").matches(".*\\d.*").matches(".*[^A-Za-z0-9].*");
        AccountEntity saved = accounts.findById(target.getId()).orElseThrow();
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo(temporary);
        assertThat(temporaryPasswords.has(target.getId())).isTrue();
        // 기존 세션은 모두 끝난다.
        mockMvc.perform(get(ME).header("Authorization", before.bearer())).andExpect(status().isUnauthorized());
        mockMvc.perform(withRefresh(post(REFRESH), before.refresh())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody("maint001", PASSWORD)))
                .andExpect(status().isUnauthorized());

        Thread.sleep(1100); // 초기화와 같은 초에 발급된 토큰은 폐기 기준에 걸린다.
        Session withTemporary = login("maint001", temporary);
        mockMvc.perform(get(ME).header("Authorization", withTemporary.bearer()))
                .andExpect(jsonPath("$.data.accountStatus").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(put(PASSWORD_URL).header("Authorization", withTemporary.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(temporaryPasswords.has(target.getId())).as("변경하면 만료 기록을 지운다").isFalse();
        assertThat(accounts.findById(target.getId()).orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    void an_expired_temporary_password_no_longer_logs_in() throws Exception {
        when(temporaryPasswordSender.send(anyString(), anyString())).thenReturn(true);
        mockMvc.perform(post(url(target.getId())).header("Authorization", adminBearer));
        String temporary = sentTemporaryPassword();

        temporaryPasswords.expire(target.getId());

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody("maint001", temporary)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        assertThat(accounts.findById(target.getId()).orElseThrow().getPasswordHash())
                .as("만료된 임시 비밀번호는 지워진다").isNull();
    }

    @Test
    void seeded_accounts_that_must_change_their_password_are_not_treated_as_expired() throws Exception {
        AccountEntity seeded = approvedAccount("seed0001", "USER");
        seeded.requirePasswordChange(LocalDateTime.now(clock));
        accounts.save(seeded);

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody("seed0001", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void an_account_without_telegram_is_refused_and_nothing_changes() throws Exception {
        AccountEntity unlinked = approvedAccount("unlinked", "USER");
        String hash = unlinked.getPasswordHash();

        mockMvc.perform(post(url(unlinked.getId())).header("Authorization", adminBearer))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("TELEGRAM_NOT_CONNECTED"));

        verify(temporaryPasswordSender, never()).send(anyString(), anyString());
        assertThat(accounts.findById(unlinked.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(changeLogs.count()).isZero();
    }

    @Test
    void a_failed_delivery_keeps_the_old_password() throws Exception {
        when(temporaryPasswordSender.send(anyString(), anyString())).thenReturn(false);
        String hash = target.getPasswordHash();

        mockMvc.perform(post(url(target.getId())).header("Authorization", adminBearer))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("TELEGRAM_DELIVERY_FAILED"));

        AccountEntity saved = accounts.findById(target.getId()).orElseThrow();
        assertThat(saved.getPasswordHash()).isEqualTo(hash);
        assertThat(saved.isMustChangePassword()).isFalse();
        assertThat(temporaryPasswords.has(target.getId())).isFalse();
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody("maint001", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void pending_deleted_and_unknown_accounts_are_not_found() throws Exception {
        AccountEntity pending = pendingAccount("waiting1");
        AccountEntity deleted = approvedAccount("gone0001", "USER");
        deleted.markDeleted(LocalDateTime.now(clock));
        accounts.save(deleted);

        for (long id : new long[] {pending.getId(), deleted.getId(), 987654321L}) {
            mockMvc.perform(post(url(id)).header("Authorization", adminBearer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }
    }

    @Test
    void the_temporary_password_appears_nowhere_but_in_the_message(CapturedOutput output) throws Exception {
        when(temporaryPasswordSender.send(anyString(), anyString())).thenReturn(true);

        String response = mockMvc.perform(post(url(target.getId())).header("Authorization", adminBearer))
                .andReturn().getResponse().getContentAsString();
        String temporary = sentTemporaryPassword();

        assertThat(response).isEmpty();
        assertThat(output.getAll()).doesNotContain(temporary);
        assertThat(changeLogs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getFieldName()).isEqualTo("비밀번호 초기화");
            assertThat(String.valueOf(log.getOldValue()) + log.getNewValue()).doesNotContain(temporary);
        });
    }
}

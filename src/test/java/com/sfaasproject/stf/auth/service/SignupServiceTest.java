package com.sfaasproject.stf.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sfaasproject.stf.auth.config.TelegramProperties;
import com.sfaasproject.stf.telegram.service.RedisTelegramLinkTokenStore;
import com.sfaasproject.stf.telegram.service.TelegramLinkTokenService;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SignupServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final RedisTelegramLinkTokenStore linkStore = mock(RedisTelegramLinkTokenStore.class);

    private SignupService service(String botUsername) {
        return new SignupService(accounts, new BCryptPasswordEncoder(), new AuthPolicyService(),
                new TelegramLinkTokenService(), linkStore, new TelegramProperties(botUsername, null));
    }

    @Test
    void without_a_bot_name_signup_fails_before_touching_any_data() {
        for (String missing : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> service(missing).signup("newuser1", "Passw0rd!x", "홍길동", Instant.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("bot-username");
        }
        verifyNoInteractions(accounts, linkStore);
    }

    @Test
    void the_bot_name_is_normalised_without_an_at_sign() {
        assertThat(new TelegramProperties("@stf_bot ", null).normalizedBotUsername()).isEqualTo("stf_bot");
        assertThat(new TelegramProperties("stf_bot", null).normalizedBotUsername()).isEqualTo("stf_bot");
        assertThat(new TelegramProperties(null, null).configured()).isFalse();
    }
}

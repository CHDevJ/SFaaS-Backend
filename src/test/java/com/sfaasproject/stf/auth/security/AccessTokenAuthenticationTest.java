package com.sfaasproject.stf.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class AccessTokenAuthenticationTest {

    private static final Claims CLAIMS = new Claims(42L, Permission.USER,
            "jti-secret-looking-value", Instant.parse("2026-11-17T01:00:00Z"),
            Instant.parse("2026-11-17T01:30:00Z"));

    private final AccessTokenAuthentication authentication = new AccessTokenAuthentication(CLAIMS);

    @Test
    void the_name_is_the_account_id_string_so_stomp_user_routing_keeps_working() {
        assertThat(authentication.getName()).isEqualTo("42");
    }

    @Test
    void the_principal_is_the_claims_for_authentication_principal_injection() {
        assertThat(authentication.getPrincipal()).isSameAs(CLAIMS);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void the_only_authority_is_the_role_of_the_position() {
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void the_token_id_does_not_leak_into_the_printed_name() {
        assertThat(authentication.getName()).doesNotContain("jti");
    }
}

package com.sfaasproject.stf.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final String SECRET = "0123456789-0123456789-0123456789-ab";

    @Test
    void a_secret_of_32_bytes_or_more_is_accepted() {
        JwtProperties properties = new JwtProperties(SECRET, "stf");

        assertThat(properties.secretBytes()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(properties.issuer()).isEqualTo("stf");
    }

    @Test
    void a_missing_or_blank_secret_stops_the_application() {
        assertThatThrownBy(() -> new JwtProperties(null, "stf")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("", "stf")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_short_secret_is_rejected_without_echoing_it() {
        assertThatThrownBy(() -> new JwtProperties("short-secret", "stf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("short-secret");
    }

    @Test
    void a_blank_issuer_is_rejected() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, " ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void the_secret_never_appears_in_toString() {
        assertThat(new JwtProperties(SECRET, "stf").toString()).doesNotContain(SECRET);
    }
}

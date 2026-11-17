package com.sfaasproject.stf.auth.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT 설정. 비밀키는 환경변수 JWT_SECRET 으로만 전달하고 저장소와 로그에 남기지 않는다.
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, @DefaultValue("stf") String issuer) {

    /** HS256 서명에 필요한 최소 키 길이(바이트). */
    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret(환경변수 JWT_SECRET)은 필수이며 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.jwt.issuer는 비어 있을 수 없습니다.");
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=****, issuer=" + issuer + "]";
    }
}

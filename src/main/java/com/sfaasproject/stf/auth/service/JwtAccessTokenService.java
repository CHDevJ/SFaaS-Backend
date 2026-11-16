package com.sfaasproject.stf.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.common.DomainException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** HS256 access JWT with explicit issuer, expiry, role, and jti checks. */
public class JwtAccessTokenService {
    public static final Duration LIFETIME = Duration.ofMinutes(30);
    private final byte[] secret;
    private final String issuer;

    public record Issued(String value, String jti, Instant expiresAt) {
        @Override public String toString() { return "Issued[redacted]"; }
    }
    public record Claims(long userId, Position position, String jti,
            Instant issuedAt, Instant expiresAt) { }

    public JwtAccessTokenService(byte[] secret, String issuer) {
        if (secret == null || secret.length < 32 || issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT requires a 256-bit secret and issuer");
        }
        this.secret = Arrays.copyOf(secret, secret.length);
        this.issuer = issuer;
    }

    public Issued issue(long userId, Position position, Instant now) {
        if (userId <= 0 || position == null || now == null) {
            throw new IllegalArgumentException("User, position and time are required");
        }
        String jti = UUID.randomUUID().toString();
        Instant expires = now.plus(LIFETIME);
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(Long.toString(userId))
                .jwtID(jti).issueTime(Date.from(now)).expirationTime(Date.from(expires))
                .claim("position", position.name()).build();
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                    .type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new MACSigner(secret));
            return new Issued(jwt.serialize(), jti, expires);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to sign access token", ex);
        }
    }

    public Claims verify(String value, Instant now) {
        if (value == null || value.isBlank() || now == null) throw invalid();
        try {
            SignedJWT jwt = SignedJWT.parse(value);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret))) throw invalid();
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date issued = claims.getIssueTime();
            Date expires = claims.getExpirationTime();
            long userId = Long.parseLong(claims.getSubject());
            String jti = claims.getJWTID();
            String role = claims.getStringClaim("position");
            if (role == null) throw invalid();
            Position position = Position.valueOf(role);
            if (!issuer.equals(claims.getIssuer()) || userId <= 0
                    || jti == null || jti.isBlank() || issued == null || expires == null
                    || issued.toInstant().isAfter(now) || !expires.toInstant().isAfter(now)) {
                throw invalid();
            }
            return new Claims(userId, position, jti, issued.toInstant(), expires.toInstant());
        } catch (ParseException | JOSEException | IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private DomainException invalid() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID",
                "다시 로그인해 주세요.");
    }
}

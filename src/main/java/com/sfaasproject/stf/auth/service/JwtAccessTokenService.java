package com.sfaasproject.stf.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
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
    public record Claims(long userId, Permission permission, String jti,
            Instant issuedAt, Instant expiresAt) {
        @Override public String toString() { return "Claims[redacted]"; }
    }

    public JwtAccessTokenService(byte[] secret, String issuer) {
        if (secret == null || secret.length < 32 || issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT requires a 256-bit secret and issuer");
        }
        this.secret = Arrays.copyOf(secret, secret.length);
        this.issuer = issuer;
    }

    public Issued issue(long userId, Permission permission, Instant now) {
        if (userId <= 0 || permission == null || now == null) {
            throw new IllegalArgumentException("User, permission and time are required");
        }
        String jti = UUID.randomUUID().toString();
        Instant expires = now.plus(LIFETIME);
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(Long.toString(userId))
                .jwtID(jti).issueTime(Date.from(now)).expirationTime(Date.from(expires))
                .claim("permission", permission.name()).claim("issuedAt", now.toString()).build();
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
            Instant exactIssued = claims.getStringClaim("issuedAt") == null ? issued == null ? null : issued.toInstant()
                    : Instant.parse(claims.getStringClaim("issuedAt"));
            if (exactIssued != null && (issued == null || exactIssued.getEpochSecond() != issued.toInstant().getEpochSecond())) throw invalid();
            long userId = Long.parseLong(claims.getSubject());
            String jti = claims.getJWTID();
            String role = claims.getStringClaim("permission");
            if (role == null) throw invalid();
            Permission permission = Permission.valueOf(role);
            if (!issuer.equals(claims.getIssuer()) || userId <= 0
                    || jti == null || jti.isBlank() || issued == null || expires == null
                    || exactIssued == null || exactIssued.isAfter(now) || !expires.toInstant().isAfter(now)) {
                throw invalid();
            }
            return new Claims(userId, permission, jti, exactIssued, expires.toInstant());
        } catch (ParseException | JOSEException | IllegalArgumentException | java.time.DateTimeException ex) {
            throw invalid();
        }
    }

    private DomainException invalid() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID",
                "다시 로그인해 주세요.");
    }
}

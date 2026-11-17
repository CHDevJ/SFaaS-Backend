package com.sfaasproject.stf.equipment.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 설비 사진의 서명 URL(API_SPEC P-18, MST-018). 화면의 {@code <img>} 는 Authorization 헤더를 보내지 못하므로
 * 사진 주소에 만료 시각과 HMAC-SHA256 서명을 붙여 10분 동안만 열리게 한다.
 * 서명 키는 JWT 서명 키에서 용도 문자열을 붙여 따로 만든다(토큰 서명에 같은 키를 그대로 쓰지 않는다).
 */
@Component
public class EquipmentPhotoUrlSigner {

    public static final Duration TTL = Duration.ofMinutes(10);
    public static final String PATH = "/api/v1/equipment-photos/";
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public EquipmentPhotoUrlSigner(@Value("${app.jwt.secret}") String secret) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(("stf-equipment-photo-url:" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, ALGORITHM);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 사진 파일 이름으로 지금부터 10분 동안 열리는 상대 주소를 만든다. */
    public String url(String fileName, Instant now) {
        long expires = now.plus(TTL).getEpochSecond();
        return PATH + fileName + "?expires=" + expires + "&signature=" + sign(fileName, expires);
    }

    /** 서명이 맞고 만료되지 않았는지. 값이 비어 있으면 false. */
    public boolean verify(String fileName, Long expires, String signature, Instant now) {
        if (fileName == null || expires == null || signature == null || now.getEpochSecond() > expires) {
            return false;
        }
        return MessageDigest.isEqual(sign(fileName, expires).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(String fileName, long expires) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] signature = mac.doFinal((fileName + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

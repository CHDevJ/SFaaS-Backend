package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.common.DomainException;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 설비 사진을 저장하기 전에 실제 파일 머리(시그니처)로 형식을 확인한다.
 * 화면설계서(S10)와 ERD 기준으로 5MB 이하의 JPG, PNG 만 받는다(API_SPEC P-18, 2026-11-17 사용자 결정).
 */
@Service
public class EquipmentPhotoValidationService {
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public enum Format {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png");

        private final String contentType;
        private final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }
    }

    public Format validate(MultipartFile photo) {
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_BYTES) throw invalid();
        byte[] header;
        try (var stream = photo.getInputStream()) {
            header = stream.readNBytes(8);
        } catch (IOException ex) {
            throw invalid();
        }
        Format format = detect(header);
        if (format == null) throw invalid();
        String declared = photo.getContentType();
        if (declared != null && !declared.isBlank()
                && !declared.equalsIgnoreCase(format.contentType())) throw invalid();
        return format;
    }

    private Format detect(byte[] header) {
        if (header.length >= 3 && (header[0] & 0xff) == 0xff
                && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) {
            return Format.JPEG;
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (header.length >= 8 && Arrays.equals(Arrays.copyOf(header, 8), png)) {
            return Format.PNG;
        }
        return null;
    }

    private DomainException invalid() {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "사진은 5MB 이하의 JPG 또는 PNG 파일이어야 합니다.");
    }
}

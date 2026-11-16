package com.sfaasproject.stf.equipment.service;

import com.sfaasproject.stf.common.DomainException;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Validates the actual image signature before photo persistence. */
@Service
public class EquipmentPhotoValidationService {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public enum Format { JPEG, PNG, WEBP }

    public Format validate(MultipartFile photo) {
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_BYTES) throw invalid();
        byte[] header;
        try (var stream = photo.getInputStream()) {
            header = stream.readNBytes(12);
        } catch (IOException ex) {
            throw invalid();
        }
        Format format = detect(header);
        if (format == null) throw invalid();
        String declared = photo.getContentType();
        if (declared != null && !declared.isBlank()
                && !declared.equalsIgnoreCase(contentType(format))) throw invalid();
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
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I'
                && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E'
                && header[10] == 'B' && header[11] == 'P') {
            return Format.WEBP;
        }
        return null;
    }

    private String contentType(Format format) {
        return switch (format) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case WEBP -> "image/webp";
        };
    }

    private DomainException invalid() {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "사진은 5MB 이하의 JPG, PNG 또는 WebP 파일이어야 합니다.");
    }
}

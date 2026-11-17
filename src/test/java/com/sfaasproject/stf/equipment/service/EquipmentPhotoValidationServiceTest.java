package com.sfaasproject.stf.equipment.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class EquipmentPhotoValidationServiceTest {
    private final EquipmentPhotoValidationService service =
            new EquipmentPhotoValidationService();

    @Test
    void acceptsRealPngAndRejectsSpoofedContentType() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        assertEquals(EquipmentPhotoValidationService.Format.PNG,
                service.validate(new MockMultipartFile("photo", "part.png", "image/png", png)));
        assertThrows(DomainException.class,
                () -> service.validate(new MockMultipartFile("photo", "part.jpg",
                        "image/jpeg", png)));
    }

    @Test
    void rejectsTextDisguisedAsImageAndOversizedFiles() {
        assertThrows(DomainException.class,
                () -> service.validate(new MockMultipartFile("photo", "part.png",
                        "image/png", "not an image".getBytes())));
        byte[] large = new byte[(int) EquipmentPhotoValidationService.MAX_BYTES + 1];
        large[0] = (byte) 0xff;
        large[1] = (byte) 0xd8;
        large[2] = (byte) 0xff;
        assertThrows(DomainException.class,
                () -> service.validate(new MockMultipartFile("photo", "part.jpg",
                        "image/jpeg", large)));
    }

    @Test
    void rejectsWebpBecauseOnlyJpgAndPngAreAllowed() {
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThrows(DomainException.class,
                () -> service.validate(new MockMultipartFile("photo", "part.webp",
                        "image/webp", webp)));
        assertThrows(DomainException.class,
                () -> service.validate(new MockMultipartFile("photo", "part.webp", null, webp)));
    }

    @Test
    void acceptsJpegWithoutDeclaredContentType() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
        assertEquals(EquipmentPhotoValidationService.Format.JPEG,
                service.validate(new MockMultipartFile("photo", "part.jpg", null, jpeg)));
    }
}

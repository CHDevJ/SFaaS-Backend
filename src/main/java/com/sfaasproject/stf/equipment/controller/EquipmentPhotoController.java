package com.sfaasproject.stf.equipment.controller;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.equipment.service.EquipmentPhotoStorage;
import com.sfaasproject.stf.equipment.service.EquipmentPhotoUrlSigner;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MST-018 설비 사진 조회. 화면의 {@code <img>} 가 쓰도록 로그인 없이 열리지만, 설비 조회 응답이 준
 * 10분짜리 서명 주소로만 열린다. 서명이 틀렸거나 만료됐거나 파일이 없으면 모두 404 로 같게 응답한다.
 */
@RestController
public class EquipmentPhotoController {

    private final EquipmentPhotoUrlSigner signer;
    private final EquipmentPhotoStorage storage;
    private final Clock clock;

    public EquipmentPhotoController(EquipmentPhotoUrlSigner signer, EquipmentPhotoStorage storage, Clock clock) {
        this.signer = signer;
        this.storage = storage;
        this.clock = clock;
    }

    @GetMapping("/api/v1/equipment-photos/{fileName}")
    public ResponseEntity<Resource> photo(@PathVariable String fileName,
            @RequestParam(required = false) Long expires, @RequestParam(required = false) String signature) {
        if (!EquipmentPhotoStorage.isValidName(fileName)
                || !signer.verify(fileName, expires, signature, clock.instant())) {
            throw notFound();
        }
        Path file = storage.find(fileName).orElseThrow(EquipmentPhotoController::notFound);
        long remaining = Math.max(0, expires - clock.instant().getEpochSecond());
        MediaType type = fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(remaining)).cachePrivate())
                .body(new FileSystemResource(file));
    }

    private static DomainException notFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "EQUIPMENT_PHOTO_NOT_FOUND", "사진을 찾을 수 없습니다.");
    }
}

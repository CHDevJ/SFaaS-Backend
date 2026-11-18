package com.sfaasproject.stf.equipment.dto;

import com.sfaasproject.stf.equipment.service.MasterEquipmentService.Command;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * MST-002 등록 요청 (openapi v7 EquipmentWriteRequest, multipart/form-data). 길이는 ERD 컬럼에 맞춘다(API_SPEC 11.0).
 * 사진 칸이 비어 있으면(파일을 고르지 않은 칸) 사진이 없는 것으로 본다.
 */
public record EquipmentWriteForm(
        @NotBlank @Size(max = 20) String equipmentCode,
        @NotBlank @Size(max = 100) String equipmentName,
        @NotBlank @Size(max = 50) String equipmentType,
        @Size(max = 50) String serialNumber,
        @NotNull @Min(1) Long lineId,
        @NotNull @Min(1) Integer processOrder,
        @NotBlank @Size(max = 20) String communicationMethod,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime registeredAt,
        MultipartFile photo) {

    public Command toCommand() {
        return new Command(equipmentCode, equipmentName, equipmentType, serialNumber, lineId, processOrder,
                communicationMethod, registeredAt, chosen(photo), false);
    }

    static MultipartFile chosen(MultipartFile photo) {
        return photo == null || photo.isEmpty() && (photo.getOriginalFilename() == null
                || photo.getOriginalFilename().isEmpty()) ? null : photo;
    }
}

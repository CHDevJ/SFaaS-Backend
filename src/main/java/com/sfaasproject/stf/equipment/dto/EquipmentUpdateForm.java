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
 * MST-003 수정 요청 (openapi v7 EquipmentUpdateRequest, multipart/form-data). 설비 코드는 받지 않는다.
 * registeredAt 을 비우면 원래 값을 둔다. photo 와 removePhoto=true 를 함께 보내면 400.
 */
public record EquipmentUpdateForm(
        @NotBlank @Size(max = 100) String equipmentName,
        @NotBlank @Size(max = 50) String equipmentType,
        @Size(max = 50) String serialNumber,
        @NotNull @Min(1) Long lineId,
        @NotNull @Min(1) Integer processOrder,
        @NotBlank @Size(max = 20) String communicationMethod,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime registeredAt,
        MultipartFile photo,
        Boolean removePhoto) {

    public Command toCommand() {
        return new Command(null, equipmentName, equipmentType, serialNumber, lineId, processOrder,
                communicationMethod, registeredAt, EquipmentWriteForm.chosen(photo), Boolean.TRUE.equals(removePhoto));
    }
}

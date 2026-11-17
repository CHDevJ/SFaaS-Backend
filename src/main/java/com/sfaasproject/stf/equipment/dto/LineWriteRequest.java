package com.sfaasproject.stf.equipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** MST-006, MST-007 요청 (openapi LineWriteRequest). 이름 길이는 ERD line.name 에 맞춘 50자다. */
public record LineWriteRequest(
        @NotBlank @Size(max = 50) String lineName,
        @NotNull @Min(1) Integer displayOrder) { }

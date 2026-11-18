package com.sfaasproject.stf.equipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * MST-006, MST-007 요청 (openapi v7 LineWriteRequest). lineType 은 선택이다. 등록에서 생략하면 PART,
 * 수정에서 생략하면 기존 값(ASSEMBLY 포함)을 유지한다.
 */
public record LineWriteRequest(
        @NotBlank @Size(max = 100) String lineName,
        @NotNull @Min(1) Integer displayOrder,
        @Pattern(regexp = "PART|ASSEMBLY") String lineType) { }

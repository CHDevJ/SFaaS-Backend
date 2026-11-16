package com.sfaasproject.stf.measurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record MeasurementIngestionRequest(
        @NotBlank @Size(max = 200) String messageId,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}-[0-9]{2}-[1-9][0-9]*$") String equipmentCode,
        @NotNull OffsetDateTime measuredAt,
        @NotEmpty @Size(max = 500) List<@Valid Value> values) {
    public record Value(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]*$") @Size(max = 50) String dataItemCode,
            @NotNull Kind valueType,
            @NotNull BigDecimal value) {
        public enum Kind { MEASUREMENT, STATE, CUMULATIVE }
    }
}

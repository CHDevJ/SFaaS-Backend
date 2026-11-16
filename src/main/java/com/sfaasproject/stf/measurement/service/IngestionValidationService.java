package com.sfaasproject.stf.measurement.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest;
import com.sfaasproject.stf.measurement.repository.IngestionStore.DataItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Validates a whole equipment batch before any value can be stored. */
@Service
public class IngestionValidationService {
    public List<DataItem> validate(MeasurementIngestionRequest request, String registeredEquipmentCode,
            List<DataItem> registeredItems) {
        Objects.requireNonNull(request, "request");
        if (request.messageId() == null || request.messageId().isBlank()
                || request.messageId().length() > 200 || request.equipmentCode() == null
                || request.measuredAt() == null || request.values() == null
                || request.values().isEmpty() || request.values().size() > 500) {
            throw invalid("수집 메시지 형식을 확인해 주세요.");
        }
        if (!request.equipmentCode().equals(registeredEquipmentCode)) {
            throw new DomainException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "설비를 찾을 수 없습니다.");
        }
        var byCode = new HashMap<String, DataItem>();
        for (DataItem item : registeredItems) byCode.put(item.code(), item);
        var seen = new HashSet<String>();
        var ordered = new ArrayList<DataItem>();
        for (var value : request.values()) {
            if (value == null || value.dataItemCode() == null || value.valueType() == null
                    || value.value() == null || !seen.add(value.dataItemCode())) {
                throw invalid("중복되거나 형식이 잘못된 데이터 항목이 있습니다.");
            }
            DataItem item = byCode.get(value.dataItemCode());
            if (item == null) {
                throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "DATA_ITEM_NOT_FOUND",
                        "등록되지 않은 데이터 항목이 있습니다.");
            }
            if (!item.kind().equals(value.valueType().name())) {
                throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                        "데이터 항목의 값 종류가 등록 정보와 다릅니다.");
            }
            if (value.valueType() == MeasurementIngestionRequest.Value.Kind.STATE
                    && !isBinary(value.value())) {
                throw invalid("상태값은 0 또는 1이어야 합니다.");
            }
            if (value.valueType() == MeasurementIngestionRequest.Value.Kind.CUMULATIVE
                    && (value.value().signum() < 0 || !isInteger(value.value()))) {
                throw invalid("누적값은 0 이상의 정수여야 합니다.");
            }
            ordered.add(item);
        }
        return List.copyOf(ordered);
    }

    private boolean isBinary(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0 || value.compareTo(BigDecimal.ONE) == 0;
    }

    private boolean isInteger(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }

    private DomainException invalid(String message) {
        return new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", message);
    }
}

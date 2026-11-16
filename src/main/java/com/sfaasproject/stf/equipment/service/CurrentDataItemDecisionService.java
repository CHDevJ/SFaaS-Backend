package com.sfaasproject.stf.equipment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** Converts one latest value to the EQP-004/005 current-item contract. */
@Service
public class CurrentDataItemDecisionService {
    public record Item(long id, String name, String valueType, String unit,
            int decimalPlaces, BigDecimal cautionThreshold, BigDecimal criticalThreshold,
            String stateLabel0, String stateLabel1) { }
    public record CurrentItem(long dataItemId, String dataItemName, String valueType,
            BigDecimal value, String displayValue, String unit, String valueStatus,
            BigDecimal cautionThreshold, BigDecimal criticalThreshold,
            LocalDateTime measuredAt) { }

    public CurrentItem map(Item item, BigDecimal value, String valueGrade,
            LocalDateTime measuredAt) {
        if (item == null || value == null || measuredAt == null) {
            throw new IllegalArgumentException("Item and latest value are required");
        }
        String display;
        String status = valueGrade;
        if ("STATE".equals(item.valueType())) {
            if (value.compareTo(BigDecimal.ZERO) == 0) display = item.stateLabel0();
            else if (value.compareTo(BigDecimal.ONE) == 0) display = item.stateLabel1();
            else throw new IllegalArgumentException("State value must be zero or one");
            if (display == null || display.isBlank()) display = value.toPlainString();
        } else if ("CUMULATIVE".equals(item.valueType())) {
            display = value.toBigIntegerExact().toString();
            status = null;
        } else if ("MEASUREMENT".equals(item.valueType())) {
            if (item.decimalPlaces() < 0 || item.decimalPlaces() > 10) {
                throw new IllegalArgumentException("Invalid decimal places");
            }
            display = value.setScale(item.decimalPlaces(), RoundingMode.HALF_UP).toPlainString();
        } else {
            throw new IllegalArgumentException("Unknown item type");
        }
        return new CurrentItem(item.id(), item.name(), item.valueType(), value, display,
                item.unit(), status, item.cautionThreshold(), item.criticalThreshold(), measuredAt);
    }
}

package com.sfaasproject.stf.measurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest;
import com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest.Value;
import com.sfaasproject.stf.measurement.repository.IngestionStore.DataItem;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionValidationServiceTest {
    private final IngestionValidationService service = new IngestionValidationService();
    private final List<DataItem> items = List.of(
            item(1, "temperature", "MEASUREMENT"),
            item(2, "run_signal", "STATE"),
            item(3, "prod_count", "CUMULATIVE"));

    @Test
    void acceptsRegisteredMixedBatchInOriginalOrder() {
        var request = batch(new Value("temperature", Value.Kind.MEASUREMENT, new BigDecimal("24.3")),
                new Value("run_signal", Value.Kind.STATE, BigDecimal.ONE),
                new Value("prod_count", Value.Kind.CUMULATIVE, new BigDecimal("101")));
        assertEquals(List.of(1L, 2L, 3L), service.validate(request, "OR-04-1", items)
                .stream().map(DataItem::id).toList());
    }

    @Test
    void rejectsWholeBatchWhenOneItemIsUnknownOrMismatched() {
        var unknown = batch(new Value("temperature", Value.Kind.MEASUREMENT, BigDecimal.TEN),
                new Value("missing", Value.Kind.MEASUREMENT, BigDecimal.ONE));
        assertEquals("DATA_ITEM_NOT_FOUND", assertThrows(DomainException.class,
                () -> service.validate(unknown, "OR-04-1", items)).code());
        var wrongKind = batch(new Value("temperature", Value.Kind.STATE, BigDecimal.ONE));
        assertEquals("VALIDATION_ERROR", assertThrows(DomainException.class,
                () -> service.validate(wrongKind, "OR-04-1", items)).code());
    }

    @Test
    void rejectsDuplicateCodeAndInvalidStateOrCounter() {
        var duplicate = batch(new Value("run_signal", Value.Kind.STATE, BigDecimal.ONE),
                new Value("run_signal", Value.Kind.STATE, BigDecimal.ZERO));
        assertThrows(DomainException.class, () -> service.validate(duplicate, "OR-04-1", items));
        assertThrows(DomainException.class, () -> service.validate(
                batch(new Value("run_signal", Value.Kind.STATE, new BigDecimal("2"))),
                "OR-04-1", items));
        assertThrows(DomainException.class, () -> service.validate(
                batch(new Value("prod_count", Value.Kind.CUMULATIVE, new BigDecimal("1.5"))),
                "OR-04-1", items));
        assertThrows(DomainException.class, () -> service.validate(
                batch(new Value("prod_count", Value.Kind.CUMULATIVE, new BigDecimal("-1"))),
                "OR-04-1", items));
    }

    private MeasurementIngestionRequest batch(Value... values) {
        return new MeasurementIngestionRequest("OR-04-1-20261116T090000.000", "OR-04-1",
                OffsetDateTime.parse("2026-11-16T09:00:00+09:00"), List.of(values));
    }

    private DataItem item(long id, String code, String kind) {
        return new DataItem(id, 1, code, code, kind, "NONE", null,
                null, null, null, null, LocalTime.MIDNIGHT);
    }
}

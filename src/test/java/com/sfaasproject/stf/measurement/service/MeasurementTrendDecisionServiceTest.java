package com.sfaasproject.stf.measurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.measurement.service.MeasurementTrendDecisionService.AlarmMarker;
import com.sfaasproject.stf.measurement.service.MeasurementTrendDecisionService.Disconnection;
import com.sfaasproject.stf.measurement.service.MeasurementTrendDecisionService.Item;
import com.sfaasproject.stf.measurement.service.MeasurementTrendDecisionService.Point;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeasurementTrendDecisionServiceTest {
    private final MeasurementTrendDecisionService service = new MeasurementTrendDecisionService();
    private final LocalDateTime from = LocalDateTime.of(2026, 11, 16, 9, 0);
    private final LocalDateTime to = from.plusMinutes(10);
    private final Item item = new Item(7, 1, "온도", "MEASUREMENT", "°C",
            new BigDecimal("80"), new BigDecimal("90"));

    @Test
    void sortsAndClipsPointsMarkersAndCommunicationIntervals() {
        var result = service.assemble(1, item, from, to,
                List.of(new Point(from.plusMinutes(3), BigDecimal.TEN),
                        new Point(from.minusSeconds(1), BigDecimal.ZERO),
                        new Point(from, BigDecimal.ONE)),
                List.of(new AlarmMarker(2, from.plusMinutes(4), BigDecimal.TEN, "CAUTION"),
                        new AlarmMarker(1, to.plusSeconds(1), BigDecimal.TEN, "CRITICAL")),
                List.of(new Disconnection(from.minusMinutes(1), from.plusMinutes(2)),
                        new Disconnection(from.plusMinutes(2), from.plusMinutes(5)),
                        new Disconnection(to.minusMinutes(1), null)), false);
        assertEquals(List.of(from, from.plusMinutes(3)), result.points().stream()
                .map(Point::measuredAt).toList());
        assertEquals(List.of(2L), result.alarmMarkers().stream()
                .map(AlarmMarker::alarmId).toList());
        assertEquals(List.of(new Disconnection(from, from.plusMinutes(5)),
                new Disconnection(to.minusMinutes(1), to)), result.disconnections());
        assertEquals(false, result.currentlyConnected());
    }

    @Test
    void rejectsReversedPeriodAndItemFromAnotherEquipment() {
        assertEquals("INVALID_PERIOD", assertThrows(DomainException.class, () -> service.assemble(
                1, item, to, from, List.of(), List.of(), List.of(), true)).code());
        assertEquals("DATA_ITEM_NOT_FOUND", assertThrows(DomainException.class, () -> service.assemble(
                2, item, from, to, List.of(), List.of(), List.of(), true)).code());
    }
}

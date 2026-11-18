package com.sfaasproject.stf.measurement.controller;

import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.measurement.dto.MeasurementTrendResponse;
import com.sfaasproject.stf.measurement.service.MeasurementTrendService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MEAS-001 데이터 항목 추세. v7 권한은 ADMIN, USER 모두이므로 로그인만 확인한다. */
@RestController
public class MeasurementTrendController {

    private final MeasurementTrendService trends;

    public MeasurementTrendController(MeasurementTrendService trends) {
        this.trends = trends;
    }

    /** dataItemIds 는 쉼표로 나눈 목록이다(openapi style=form, explode=false). */
    @GetMapping("/api/v1/equipment/{equipmentId}/measurements")
    public ApiResponse<MeasurementTrendResponse> trend(@PathVariable long equipmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam List<Long> dataItemIds) {
        return ApiResponse.ok(MeasurementTrendResponse.from(trends.trend(equipmentId, from, to, dataItemIds)));
    }
}

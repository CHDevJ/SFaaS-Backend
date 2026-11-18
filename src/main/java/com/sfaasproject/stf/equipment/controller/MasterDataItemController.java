package com.sfaasproject.stf.equipment.controller;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.equipment.dto.DataItemWriteRequest;
import com.sfaasproject.stf.equipment.dto.MasterDataItemResponse;
import com.sfaasproject.stf.equipment.dto.ThresholdWriteRequest;
import com.sfaasproject.stf.equipment.service.MasterDataItemService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MST-010~013 설비 데이터 항목과 알람 기준. v7 권한은 ADMIN, USER 모두이므로 로그인만 확인한다. */
@RestController
@RequestMapping("/api/v1/master")
public class MasterDataItemController {

    private final MasterDataItemService dataItems;

    public MasterDataItemController(MasterDataItemService dataItems) {
        this.dataItems = dataItems;
    }

    @GetMapping("/equipment/{equipmentId}/data-items")
    public ApiResponse<List<MasterDataItemResponse>> list(@PathVariable long equipmentId) {
        return ApiResponse.ok(dataItems.list(equipmentId).stream().map(MasterDataItemResponse::from).toList());
    }

    @PostMapping("/equipment/{equipmentId}/data-items")
    public ResponseEntity<ApiResponse<MasterDataItemResponse>> create(@PathVariable long equipmentId,
            @Valid @RequestBody DataItemWriteRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        MasterDataItemResponse body = MasterDataItemResponse.from(
                dataItems.create(equipmentId, request.toCommand(), actor.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(body));
    }

    @PutMapping("/data-items/{dataItemId}")
    public ApiResponse<MasterDataItemResponse> update(@PathVariable long dataItemId,
            @Valid @RequestBody DataItemWriteRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        return ApiResponse.ok(MasterDataItemResponse.from(
                dataItems.update(dataItemId, request.toCommand(), actor.userId())));
    }

    @PutMapping("/data-items/{dataItemId}/threshold")
    public ApiResponse<MasterDataItemResponse.AppliedThreshold> updateThreshold(@PathVariable long dataItemId,
            @Valid @RequestBody ThresholdWriteRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        boolean measurement = request.hasMeasurementFields();
        boolean state = request.hasStateFields();
        if (measurement == state) {
            // 두 모양 중 정확히 하나여야 한다(openapi oneOf).
            throw new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
        }
        return ApiResponse.ok(MasterDataItemResponse.AppliedThreshold.from(dataItems.updateThreshold(dataItemId,
                measurement ? request.threshold() : null, state ? request.stateAlarm() : null, actor.userId())));
    }
}

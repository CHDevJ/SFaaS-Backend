package com.sfaasproject.stf.equipment.controller;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.equipment.dto.LineLayoutUpdateRequest;
import com.sfaasproject.stf.equipment.dto.LineWriteRequest;
import com.sfaasproject.stf.equipment.dto.MasterLineResponse;
import com.sfaasproject.stf.equipment.service.MasterLineService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MST-005~009 라인과 설비 배치 기준정보. v7 권한은 ADMIN, USER 모두이므로
 * 로그인만 확인한다(SecurityConfig 의 anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/v1/master/lines")
public class MasterLineController {

    private final MasterLineService lines;

    public MasterLineController(MasterLineService lines) {
        this.lines = lines;
    }

    @GetMapping
    public ApiResponse<List<MasterLineResponse>> list() {
        return ApiResponse.ok(lines.list().stream().map(MasterLineResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MasterLineResponse>> create(@Valid @RequestBody LineWriteRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        MasterLineResponse body = MasterLineResponse.from(
                lines.create(request.lineName(), request.displayOrder(), request.lineType(), actor.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(body));
    }

    @PutMapping("/{lineId}")
    public ApiResponse<MasterLineResponse> update(@PathVariable long lineId,
            @Valid @RequestBody LineWriteRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        return ApiResponse.ok(MasterLineResponse.from(
                lines.update(lineId, request.lineName(), request.displayOrder(), request.lineType(),
                        actor.userId())));
    }

    @DeleteMapping("/{lineId}")
    public ResponseEntity<Void> delete(@PathVariable long lineId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        lines.delete(lineId, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{lineId}/layout")
    public ApiResponse<MasterLineResponse> updateLayout(@PathVariable long lineId,
            @Valid @RequestBody LineLayoutUpdateRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        return ApiResponse.ok(MasterLineResponse.from(
                lines.updateLayout(lineId, request.toItems(), actor.userId())));
    }
}

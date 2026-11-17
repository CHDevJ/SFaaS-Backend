package com.sfaasproject.stf.equipment.controller;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.common.PageMeta;
import com.sfaasproject.stf.common.PagedApiResponse;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.equipment.dto.EquipmentUpdateForm;
import com.sfaasproject.stf.equipment.dto.EquipmentWriteForm;
import com.sfaasproject.stf.equipment.dto.MasterEquipmentResponse;
import com.sfaasproject.stf.equipment.service.MasterEquipmentService;
import com.sfaasproject.stf.equipment.service.MasterEquipmentService.EquipmentPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MST-001~004 설비 기준정보. 권한 매트릭스상 모든 직책이 쓸 수 있으므로 로그인만 확인한다.
 * 등록과 수정은 사진 때문에 multipart/form-data 로 받는다.
 */
@RestController
@RequestMapping("/api/v1/master/equipment")
public class MasterEquipmentController {

    private final MasterEquipmentService equipment;

    public MasterEquipmentController(MasterEquipmentService equipment) {
        this.equipment = equipment;
    }

    @GetMapping
    public PagedApiResponse<MasterEquipmentResponse.PageData> list(
            @RequestParam(required = false) Long lineId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        EquipmentPage result = equipment.list(lineId, equipmentId, includeInactive, PageQuery.of(page, size));
        return PagedApiResponse.ok(MasterEquipmentResponse.PageData.from(result), PageMeta.from(result.items()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MasterEquipmentResponse>> create(@Valid @ModelAttribute EquipmentWriteForm form,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        MasterEquipmentResponse body = MasterEquipmentResponse.from(equipment.create(form.toCommand(), actor.userId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(body));
    }

    @PutMapping(path = "/{equipmentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MasterEquipmentResponse> update(@PathVariable long equipmentId,
            @Valid @ModelAttribute EquipmentUpdateForm form,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        return ApiResponse.ok(MasterEquipmentResponse.from(
                equipment.update(equipmentId, form.toCommand(), actor.userId())));
    }

    @DeleteMapping("/{equipmentId}")
    public ResponseEntity<Void> delete(@PathVariable long equipmentId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        equipment.delete(equipmentId, actor.userId());
        return ResponseEntity.noContent().build();
    }
}

package com.sfaasproject.stf.equipment.controller;

import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.common.PageMeta;
import com.sfaasproject.stf.common.PagedApiResponse;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.equipment.dto.CommunicationResponse;
import com.sfaasproject.stf.equipment.service.CommunicationStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** COM-001/002 통신 현황. v7 권한은 ADMIN, USER 모두이므로 로그인만 확인한다. */
@RestController
@RequestMapping("/api/v1/communications")
public class CommunicationController {

    private final CommunicationStatusService communications;

    public CommunicationController(CommunicationStatusService communications) {
        this.communications = communications;
    }

    /** COM-001 */
    @GetMapping
    public PagedApiResponse<CommunicationResponse.PageData> list(
            @RequestParam(required = false) Long lineId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        CommunicationStatusService.EquipmentPage result = communications.list(lineId, status, PageQuery.of(page, size));
        return PagedApiResponse.ok(CommunicationResponse.PageData.from(result), PageMeta.from(result.items()));
    }

    /** COM-002 */
    @GetMapping("/summary")
    public ApiResponse<CommunicationResponse.Summary> summary() {
        return ApiResponse.ok(CommunicationResponse.Summary.from(communications.summary()));
    }
}

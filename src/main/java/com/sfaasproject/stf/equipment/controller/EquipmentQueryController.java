package com.sfaasproject.stf.equipment.controller;

import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.equipment.dto.EquipmentQueryResponse;
import com.sfaasproject.stf.equipment.service.EquipmentQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** EQP-002~005 설비 현황 조회. v7 권한은 ADMIN, USER 모두이므로 로그인만 확인한다. */
@RestController
@RequestMapping("/api/v1")
public class EquipmentQueryController {

    private final EquipmentQueryService queries;

    public EquipmentQueryController(EquipmentQueryService queries) {
        this.queries = queries;
    }

    /** EQP-002 */
    @GetMapping("/lines/{lineId}/processes")
    public ApiResponse<List<EquipmentQueryResponse.Process>> processes(@PathVariable long lineId) {
        return ApiResponse.ok(queries.processes(lineId).stream().map(EquipmentQueryResponse.Process::from).toList());
    }

    /** EQP-003 */
    @GetMapping("/processes/{processId}/equipment")
    public ApiResponse<List<EquipmentQueryResponse.Summary>> processEquipment(@PathVariable long processId) {
        return ApiResponse.ok(queries.processEquipment(processId).stream()
                .map(EquipmentQueryResponse.Summary::from).toList());
    }

    /** EQP-004 */
    @GetMapping("/equipment/{equipmentId}")
    public ApiResponse<EquipmentQueryResponse.Detail> detail(@PathVariable long equipmentId) {
        return ApiResponse.ok(EquipmentQueryResponse.Detail.from(queries.detail(equipmentId)));
    }

    /** EQP-005 */
    @GetMapping("/equipment/{equipmentId}/data-items")
    public ApiResponse<List<EquipmentQueryResponse.CurrentItem>> dataItems(@PathVariable long equipmentId) {
        return ApiResponse.ok(queries.dataItems(equipmentId).stream()
                .map(EquipmentQueryResponse.CurrentItem::from).toList());
    }
}

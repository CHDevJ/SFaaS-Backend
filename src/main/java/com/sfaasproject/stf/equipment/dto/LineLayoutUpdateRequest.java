package com.sfaasproject.stf.equipment.dto;

import com.sfaasproject.stf.equipment.service.MasterLineService.LayoutItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** MST-009 요청 (openapi LineLayoutUpdateRequest). */
public record LineLayoutUpdateRequest(@NotEmpty List<@Valid @NotNull Item> equipment) {

    /** openapi LineLayoutEquipment. */
    public record Item(@NotNull @Min(1) Long equipmentId, @NotNull @Min(1) Integer processOrder) { }

    public List<LayoutItem> toItems() {
        return equipment.stream().map(item -> new LayoutItem(item.equipmentId(), item.processOrder())).toList();
    }
}

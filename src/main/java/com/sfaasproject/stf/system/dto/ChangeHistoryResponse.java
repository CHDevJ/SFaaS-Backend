package com.sfaasproject.stf.system.dto;

import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.system.service.ChangeHistoryService;
import java.time.OffsetDateTime;

/**
 * AUD-001 이력 한 줄 (openapi ChangeHistory). fieldName 은 v7 에 없던 "바꾼 항목"(change_log.field_name)이다
 * (2026-11-18 A 추가).
 */
public record ChangeHistoryResponse(long historyId, OffsetDateTime changedAt, Changer changer, String itemType,
        String itemName, String fieldName, String previousValue, String changedValue) {

    /** openapi UserBrief. */
    public record Changer(long userId, String name, String permission) { }

    public static ChangeHistoryResponse from(ChangeHistoryService.History history) {
        var c = history.changer();
        return new ChangeHistoryResponse(history.historyId(),
                KstTime.toOffset(KstTime.toInstant(history.changedAt())), new Changer(c.userId(), c.name(),
                        c.permission()), history.itemType(), history.itemName(), history.fieldName(),
                history.previousValue(), history.changedValue());
    }
}

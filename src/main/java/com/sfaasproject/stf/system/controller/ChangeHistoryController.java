package com.sfaasproject.stf.system.controller;

import com.sfaasproject.stf.common.PageMeta;
import com.sfaasproject.stf.common.PagedApiResponse;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.system.dto.ChangeHistoryResponse;
import com.sfaasproject.stf.system.service.ChangeHistoryService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AUD-001 설정 변경 이력. /api/v1/system/** 는 SecurityConfig 에서 ADMIN 만 허용한다. */
@RestController
public class ChangeHistoryController {

    private final ChangeHistoryService histories;

    public ChangeHistoryController(ChangeHistoryService histories) {
        this.histories = histories;
    }

    @GetMapping("/api/v1/system/change-histories")
    public PagedApiResponse<List<ChangeHistoryResponse>> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Page<ChangeHistoryService.History> found = histories.search(from, to, itemType, keyword,
                PageQuery.of(page, size));
        return PagedApiResponse.ok(found.getContent().stream().map(ChangeHistoryResponse::from).toList(),
                PageMeta.from(found));
    }
}

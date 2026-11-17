package com.sfaasproject.stf.user.controller;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.common.PageMeta;
import com.sfaasproject.stf.common.PagedApiResponse;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.user.dto.SignupApprovalRequest;
import com.sfaasproject.stf.user.dto.SignupRejectionRequest;
import com.sfaasproject.stf.user.dto.SignupRequestSummaryResponse;
import com.sfaasproject.stf.user.dto.UserResponse;
import com.sfaasproject.stf.user.service.SignupApprovalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * APR-001 승인 대기 목록, APR-002 승인, APR-003 거절. 시스템관리자 전용이며
 * 접근 제어는 {@code /api/v1/system/**} 규칙(SecurityConfig)이 맡는다.
 */
@RestController
@RequestMapping("/api/v1/system/signup-requests")
public class SignupRequestController {

    private final SignupApprovalService approvals;

    public SignupRequestController(SignupApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping
    public PagedApiResponse<List<SignupRequestSummaryResponse>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Page<SignupApprovalService.PendingSignup> result = approvals.listPending(PageQuery.of(page, size));
        return PagedApiResponse.ok(result.map(SignupRequestSummaryResponse::from).getContent(),
                PageMeta.from(result));
    }

    @PostMapping("/{requestId}/approval")
    public ApiResponse<UserResponse> approve(@PathVariable long requestId,
            @Valid @RequestBody SignupApprovalRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims approver) {
        return ApiResponse.ok(UserResponse.from(
                approvals.approve(requestId, request.position(), approver.userId())));
    }

    @PostMapping("/{requestId}/rejection")
    public ResponseEntity<Void> reject(@PathVariable long requestId,
            @Valid @RequestBody SignupRejectionRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims approver) {
        approvals.reject(requestId, request.reason(), approver.userId());
        return ResponseEntity.noContent().build();
    }
}

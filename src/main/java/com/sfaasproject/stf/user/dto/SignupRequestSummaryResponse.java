package com.sfaasproject.stf.user.dto;

import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.user.service.SignupApprovalService;
import java.time.OffsetDateTime;

/**
 * APR-001 목록의 한 항목. 가입 응답(Signup)과 달리 연결 주소(telegramLink)는 없다.
 * 연결 토큰은 해시로만 저장하고 10분 뒤 만료되므로 이미 접수된 신청의 주소는 만들 수 없고,
 * 승인 화면에는 연결 "여부"만 필요하다(FR0107).
 */
public record SignupRequestSummaryResponse(
        long requestId,
        String loginId,
        String name,
        AccountStatus accountStatus,
        OffsetDateTime requestedAt,
        boolean telegramConnected) {

    public static SignupRequestSummaryResponse from(SignupApprovalService.PendingSignup pending) {
        return new SignupRequestSummaryResponse(pending.requestId(), pending.loginId(), pending.name(),
                pending.accountStatus(), KstTime.toOffset(pending.requestedAt()), pending.telegramConnected());
    }
}

package com.sfaasproject.stf.user.dto;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import jakarta.validation.constraints.NotNull;

/** APR-002 요청. 승인하면서 신청자의 직책을 정한다. */
public record SignupApprovalRequest(@NotNull Position position) {
}

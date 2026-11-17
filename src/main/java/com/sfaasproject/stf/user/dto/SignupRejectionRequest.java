package com.sfaasproject.stf.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** APR-003 요청. 거절 사유는 변경 이력과 신청자 알림에 쓴다. */
public record SignupRejectionRequest(@NotBlank @Size(max = 500) String reason) {
}

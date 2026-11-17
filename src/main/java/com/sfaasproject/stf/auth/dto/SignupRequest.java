package com.sfaasproject.stf.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AUTH-005 요청. 직책은 신청자가 고르지 않는다. 형식 검사는 서비스가 하며,
 * 비밀번호가 로그에 남지 않도록 {@link #toString()}에서 가린다.
 */
public record SignupRequest(@NotBlank String loginId, @NotBlank String password, @NotBlank String name) {

    @Override
    public String toString() {
        return "SignupRequest[loginId=" + loginId + ", password=****, name=" + name + "]";
    }
}

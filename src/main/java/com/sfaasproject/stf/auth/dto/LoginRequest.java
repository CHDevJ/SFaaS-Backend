package com.sfaasproject.stf.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AUTH-001 요청. 형식(아이디 4~20자 영문 소문자·숫자, 비밀번호 8~64자 조합)은 서비스가 검사한다.
 * 비밀번호가 로그에 남지 않도록 {@link #toString()}에서 가린다.
 */
public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {

    @Override
    public String toString() {
        return "LoginRequest[loginId=" + loginId + ", password=****]";
    }
}

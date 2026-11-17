package com.sfaasproject.stf.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AUTH-006 요청. 새 비밀번호의 형식(8~64자, 영문·숫자·특수문자)은 서비스가 검사해서
 * PASSWORD_POLICY_VIOLATION 으로 응답한다. 비밀번호가 로그에 남지 않도록 {@link #toString()}에서 가린다.
 */
public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[currentPassword=****, newPassword=****]";
    }
}

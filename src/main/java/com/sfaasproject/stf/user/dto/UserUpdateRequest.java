package com.sfaasproject.stf.user.dto;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** USR-003 요청. 권한은 직책에서 파생되므로 따로 받지 않는다. */
public record UserUpdateRequest(@NotBlank String name, @NotNull Position position) {
}

package com.sfaasproject.stf.user.controller;

import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.common.PageMeta;
import com.sfaasproject.stf.common.PagedApiResponse;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.user.dto.UserResponse;
import com.sfaasproject.stf.user.dto.UserUpdateRequest;
import com.sfaasproject.stf.user.service.PasswordResetService;
import com.sfaasproject.stf.user.service.UserAdminService;
import com.sfaasproject.stf.user.service.UserView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * USR-001 목록, USR-002 상세, USR-003 수정, USR-004 삭제. 시스템관리자 전용이며
 * 접근 제어는 {@code /api/v1/system/**} 규칙(SecurityConfig)이 맡는다.
 */
@RestController
@RequestMapping("/api/v1/system/users")
public class SystemUserController {

    private final UserAdminService users;
    private final PasswordResetService passwordReset;

    public SystemUserController(UserAdminService users, PasswordResetService passwordReset) {
        this.users = users;
        this.passwordReset = passwordReset;
    }

    @GetMapping
    public PagedApiResponse<List<UserResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Position position,
            @RequestParam(required = false) Permission permission,
            @RequestParam(required = false) AccountStatus accountStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Page<UserView> result = users.list(
                new UserAdminService.UserFilter(keyword, position, permission, accountStatus),
                PageQuery.of(page, size), sort);
        return PagedApiResponse.ok(result.map(UserResponse::forList).getContent(), PageMeta.from(result));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> get(@PathVariable long userId) {
        return ApiResponse.ok(UserResponse.from(users.get(userId)));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> update(@PathVariable long userId,
            @Valid @RequestBody UserUpdateRequest request,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        return ApiResponse.ok(UserResponse.from(
                users.update(userId, request.name(), request.position(), actor.userId())));
    }

    @PostMapping("/{userId}/password-reset")
    public ResponseEntity<Void> resetPassword(@PathVariable long userId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        passwordReset.reset(userId, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable long userId,
            @AuthenticationPrincipal JwtAccessTokenService.Claims actor) {
        users.delete(userId, actor.userId());
        return ResponseEntity.noContent().build();
    }
}

package com.sfaasproject.stf.auth.controller;

import com.sfaasproject.stf.auth.dto.CurrentUserResponse;
import com.sfaasproject.stf.auth.dto.LoginRequest;
import com.sfaasproject.stf.auth.dto.LoginResponse;
import com.sfaasproject.stf.auth.dto.PasswordChangeRequest;
import com.sfaasproject.stf.auth.dto.RefreshTokenRequest;
import com.sfaasproject.stf.auth.dto.SignupRequest;
import com.sfaasproject.stf.auth.dto.SignupResponse;
import com.sfaasproject.stf.auth.dto.TokenResponse;
import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.auth.service.RefreshCookieService;
import com.sfaasproject.stf.auth.service.SignupService;
import com.sfaasproject.stf.common.ApiResponse;
import com.sfaasproject.stf.common.DomainException;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AUTH-001 로그인, 002 갱신, 003 로그아웃, 004 내 정보, 005 회원가입 신청, 006 비밀번호 변경. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REFRESH_INVALID = "AUTH_REFRESH_TOKEN_INVALID";

    private final AuthFlowService authFlow;
    private final SignupService signupService;
    private final RefreshCookieService refreshCookies;
    private final Clock clock;

    public AuthController(AuthFlowService authFlow, SignupService signupService,
            RefreshCookieService refreshCookies, Clock clock) {
        this.authFlow = authFlow;
        this.signupService = signupService;
        this.refreshCookies = refreshCookies;
        this.clock = clock;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthFlowService.Login login = authFlow.login(request.loginId(), request.password(), clock.instant());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(login.refreshToken()).toString())
                .body(ApiResponse.ok(LoginResponse.from(login)));
    }

    /**
     * Refresh Token 을 회전시켜 새 Access Token 을 발급한다. 새 Refresh Token 은 쿠키로만 내려간다.
     * 실패하면(만료, 재사용 감지 등) 남아 있는 쿠키도 지워서 브라우저가 무효한 토큰을 계속 보내지 않게 한다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = RefreshCookieService.NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest body) {
        String raw = cookieToken != null && !cookieToken.isBlank() ? cookieToken
                : body == null ? null : body.refreshToken();
        try {
            AuthFlowService.Refreshed refreshed = authFlow.refresh(raw, clock.instant());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(refreshed.refreshToken()).toString())
                    .body(ApiResponse.ok(TokenResponse.from(refreshed)));
        } catch (DomainException ex) {
            if (!REFRESH_INVALID.equals(ex.code())) {
                throw ex;
            }
            return ResponseEntity.status(ex.status())
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                    .body(errorBody(ex));
        }
    }

    /** 현재 Access Token 과 Refresh 계열을 폐기하고 쿠키를 지운다. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @CookieValue(name = RefreshCookieService.NAME, required = false) String refreshToken) {
        authFlow.logout(bearerToken(authorization), refreshToken, clock.instant());
        return noContentClearingCookie();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ApiResponse.ok(CurrentUserResponse.from(
                authFlow.currentUser(bearerToken(authorization), clock.instant())));
    }

    @PostMapping("/signups")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupService.Signup signup = signupService.signup(request.loginId(), request.password(),
                request.name(), clock.instant());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(SignupResponse.from(signup)));
    }

    /** 비밀번호를 바꾸면 현재 세션을 포함한 모든 토큰이 폐기되므로 쿠키도 지우고 다시 로그인하게 한다. */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody PasswordChangeRequest request) {
        authFlow.changePassword(bearerToken(authorization), request.currentPassword(),
                request.newPassword(), clock.instant());
        return noContentClearingCookie();
    }

    private ResponseEntity<Void> noContentClearingCookie() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    /** 보안 필터가 이미 검증한 헤더에서 토큰 값만 꺼낸다. */
    private static String bearerToken(String authorization) {
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "다시 로그인해 주세요.");
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private static Map<String, Object> errorBody(DomainException ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", ex.code());
        error.put("message", ex.getMessage());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", false);
        root.put("error", error);
        return root;
    }
}

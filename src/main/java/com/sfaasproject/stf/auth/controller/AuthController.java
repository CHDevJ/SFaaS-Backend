package com.sfaasproject.stf.auth.controller;

import com.sfaasproject.stf.auth.dto.LoginRequest;
import com.sfaasproject.stf.auth.dto.LoginResponse;
import com.sfaasproject.stf.auth.dto.SignupRequest;
import com.sfaasproject.stf.auth.dto.SignupResponse;
import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.auth.service.RefreshCookieService;
import com.sfaasproject.stf.auth.service.SignupService;
import com.sfaasproject.stf.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AUTH-001 로그인, AUTH-005 회원가입 신청. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

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

    @PostMapping("/signups")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupService.Signup signup = signupService.signup(request.loginId(), request.password(),
                request.name(), clock.instant());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(SignupResponse.from(signup)));
    }
}

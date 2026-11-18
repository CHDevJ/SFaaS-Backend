package com.sfaasproject.stf.auth.security;

import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import java.time.Clock;
import com.sfaasproject.stf.measurement.security.InternalApiKeyAuthenticationFilter;
import com.sfaasproject.stf.measurement.security.InternalApiKeyVerifier;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Bearer JWT 기반 무상태 인증 설정. 폼 로그인, 세션, HTTP Basic 은 쓰지 않는다.
 * Access Token 은 Authorization 헤더로 보내고 Refresh Token 은 쿠키를 쓰는 갱신 API 에서만 읽으므로,
 * 그 밖의 요청은 쿠키로 인증되지 않아 CSRF 보호는 끈다.
 *
 * <p>접근 규칙은 docs/openapi.yaml 의 x-permissions 를 따른다.
 * {@code /api/v1/system/**} 은 기본적으로 시스템관리자 전용이지만, 알람 당번 조회(DUT-001, DUT-003)는
 * 세 직책 모두 읽을 수 있고 등록·수정·삭제만 시스템관리자 전용이다.
 */
@Configuration
public class SecurityConfig {

    /** 토큰 없이 호출하는 POST API. 필터도 이 경로는 건너뛴다. */
    static final Set<String> PUBLIC_POST_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/signups",
            "/api/v1/auth/refresh");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAccessTokenService jwt,
            AuthFlowService.SessionStore sessions, AuthFlowService.AccountStore accounts, Clock clock,
            RestAuthenticationEntryPoint entryPoint, RestAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<InternalApiKeyVerifier> internalKeys) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // HTTP handshake is public; STOMP CONNECT validates the JWT separately.
                        .requestMatchers(HttpMethod.GET, "/ws", "/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/v1/telegram/updates").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/v1/measurements").hasRole("NODE_RED")
                        .requestMatchers("/api/v1/system/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new InternalApiKeyAuthenticationFilter(internalKeys, entryPoint),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AccessTokenAuthenticationFilter(jwt, sessions, accounts, clock, entryPoint),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

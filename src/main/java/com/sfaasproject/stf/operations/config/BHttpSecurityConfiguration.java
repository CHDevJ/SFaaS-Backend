package com.sfaasproject.stf.operations.config;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.operations.port.BPorts.Users;
import com.sfaasproject.stf.operations.service.BTokenGuard;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class BHttpSecurityConfiguration {
    @Bean
    public BTokenGuard bTokenGuard(
            org.springframework.data.redis.core.StringRedisTemplate redis,
            org.springframework.beans.factory.ObjectProvider<
                            com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore>
                    sessions) {
        return new BTokenGuard(redis, sessions);
    }

    @Bean
    public JwtAccessTokenService bJwt(
            @Value("${stf.auth.jwt-secret}") String secret,
            @Value("${stf.auth.issuer:stf}") String issuer) {
        return new JwtAccessTokenService(secret.getBytes(StandardCharsets.UTF_8), issuer);
    }

    @Bean
    public SecurityFilterChain bSecurity(
            HttpSecurity http,
            JwtAccessTokenService jwt,
            Users users,
            Clock backendBClock,
            BTokenGuard tokens,
            org.springframework.beans.factory.ObjectProvider<
                            com.sfaasproject.stf.measurement.security.InternalApiKeyVerifier>
                    internalKeys)
            throws Exception {
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(
                        c ->
                                c.ignoringRequestMatchers(
                                        "/api/v1/**", "/ws/**", "/internal/v1/measurements"))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                "/actuator/health",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/ws",
                                                "/ws/**",
                                                "/api/v1/auth/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                        (request, response, ex) ->
                                                error(response, 401, "AUTH_TOKEN_INVALID")))
                .addFilterBefore(
                        new OncePerRequestFilter() {
                            @Override
                            protected void doFilterInternal(
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
                                    throws ServletException, IOException {
                                if (request.getRequestURI().equals("/internal/v1/measurements")) {
                                    var key = internalKeys.getIfAvailable();
                                    if (key == null
                                            || !key.matches(
                                                    request.getHeader("X-Internal-Api-Key"))) {
                                        error(response, 401, "AUTH_TOKEN_INVALID");
                                        return;
                                    }
                                    SecurityContextHolder.getContext()
                                            .setAuthentication(
                                                    new UsernamePasswordAuthenticationToken(
                                                            "NODE_RED",
                                                            null,
                                                            List.of(
                                                                    new SimpleGrantedAuthority(
                                                                            "ROLE_NODE_RED"))));
                                }
                                String token = request.getHeader("Authorization");
                                if (token != null && token.startsWith("Bearer ")) {
                                    try {
                                        var claims =
                                                jwt.verify(
                                                        token.substring(7),
                                                        backendBClock.instant());
                                        var user = users.active(claims.userId());
                                        if (user == null || tokens.revoked(claims)) {
                                            error(response, 401, "AUTH_TOKEN_INVALID");
                                            return;
                                        }
                                        SecurityContextHolder.getContext()
                                                .setAuthentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                Long.toString(user.userId()),
                                                                null,
                                                                List.of(
                                                                        new SimpleGrantedAuthority(
                                                                                "ROLE_"
                                                                                        + user.position()
                                                                                                .name()))));
                                    } catch (RuntimeException invalid) {
                                        error(response, 401, "AUTH_TOKEN_INVALID");
                                        return;
                                    }
                                }
                                try {
                                    chain.doFilter(request, response);
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            }
                        },
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void error(HttpServletResponse response, int status, String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter()
                .write(
                        "{\"success\":false,\"error\":{\"code\":\""
                                + code
                                + "\",\"message\":\"다시 로그인해 주세요.\"}}");
    }
}

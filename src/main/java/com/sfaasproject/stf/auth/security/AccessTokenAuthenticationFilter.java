package com.sfaasproject.stf.auth.security;

import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.common.DomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 토큰을 검증해 로그인 사용자를 만든다.
 *
 * <ol>
 *   <li>서명, 만료, 발급자를 확인한다.
 *   <li>로그아웃이나 비밀번호 변경으로 폐기된 토큰이면 거부한다.
 *   <li>계정이 지금도 유효한지 DB 에서 다시 확인한다. 삭제됐거나 승인 대기이거나 직책이 없으면 거부한다.
 *   <li>권한은 토큰이 아니라 <b>DB 의 현재 직책</b>으로 정한다. 직책을 바꾸면 바로 반영된다.
 * </ol>
 *
 * 확인할 수 없는 상황(폐기 저장소나 DB 오류)에서는 거부한다(fail-closed).
 *
 * <p>로그인과 회원가입은 토큰 없이 쓰는 API 이므로 헤더에 만료된 토큰이 남아 있어도 검사하지 않는다.
 * 이 필터는 보안 설정에서만 등록한다(서블릿 필터 빈으로 만들면 두 번 실행된다).
 */
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAccessTokenService jwt;
    private final AuthFlowService.SessionStore sessions;
    private final AuthFlowService.AccountStore accounts;
    private final Clock clock;
    private final AuthenticationEntryPoint entryPoint;

    public AccessTokenAuthenticationFilter(JwtAccessTokenService jwt, AuthFlowService.SessionStore sessions,
            AuthFlowService.AccountStore accounts, Clock clock, AuthenticationEntryPoint entryPoint) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.accounts = accounts;
        this.clock = clock;
        this.entryPoint = entryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && SecurityConfig.PUBLIC_POST_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            chain.doFilter(request, response); // 토큰이 없으면 뒤의 권한 검사가 401 로 처리한다.
            return;
        }
        Authentication authentication;
        try {
            authentication = authenticate(header.substring(BEARER_PREFIX.length()).trim());
        } catch (DomainException ex) {
            reject(request, response);
            return;
        } catch (RuntimeException ex) {
            log.error("Access token check failed; rejecting the request.", ex);
            reject(request, response);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private Authentication authenticate(String token) {
        JwtAccessTokenService.Claims claims = jwt.verify(token, clock.instant());
        if (sessions.isAccessRevoked(claims.jti(), claims.userId(), claims.issuedAt())) {
            throw invalid();
        }
        AuthFlowService.Account account = accounts.findById(claims.userId());
        if (account == null || account.status() == AccountStatus.PENDING_APPROVAL
                || account.status() == null || account.position() == null) {
            throw invalid();
        }
        JwtAccessTokenService.Claims current = new JwtAccessTokenService.Claims(claims.userId(),
                account.position(), claims.jti(), claims.issuedAt(), claims.expiresAt());
        return new AccessTokenAuthentication(current);
    }

    private static DomainException invalid() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "다시 로그인해 주세요.");
    }

    private void reject(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        entryPoint.commence(request, response, new BadCredentialsException("Invalid access token"));
    }
}

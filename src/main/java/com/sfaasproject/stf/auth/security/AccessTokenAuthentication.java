package com.sfaasproject.stf.auth.security;

import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 검증이 끝난 Access Token 으로 만든 로그인 정보.
 *
 * <ul>
 *   <li>{@link #getPrincipal()}: 현재 DB 기준 직책이 담긴 {@link JwtAccessTokenService.Claims}.
 *       컨트롤러는 {@code @AuthenticationPrincipal Claims} 로 받는다.
 *   <li>{@link #getName()}: 계정 번호 문자열. STOMP 개인 알림 라우팅처럼 이름이 필요한 곳에서 쓴다.
 *   <li>권한: {@code ROLE_<직책>} 하나.
 * </ul>
 *
 * 자동 생성되는 record 의 toString 에는 토큰 식별자(jti)가 들어 있어서, 로그에 사용자 이름이 찍히더라도
 * 노출되지 않도록 {@link #getName()}을 따로 정의했다.
 */
public class AccessTokenAuthentication extends AbstractAuthenticationToken {

    private final JwtAccessTokenService.Claims claims;

    public AccessTokenAuthentication(JwtAccessTokenService.Claims claims) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + claims.position().name())));
        this.claims = claims;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public JwtAccessTokenService.Claims getPrincipal() {
        return claims;
    }

    @Override
    public String getName() {
        return Long.toString(claims.userId());
    }
}

package com.sfaasproject.stf.auth.config;

import com.sfaasproject.stf.auth.service.AuthFlowService;
import com.sfaasproject.stf.auth.service.AuthPolicyService;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.auth.service.RefreshTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 인증 서비스 조립. 업무 규칙은 {@link AuthFlowService} 에 있고 여기서는 저장소와 연결만 한다. */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, TelegramProperties.class})
public class AuthConfig {

    /** ERD 가 BCrypt 로 저장하도록 정했고 초기 데이터의 해시도 접두어 없는 BCrypt 이므로 그대로 쓴다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAccessTokenService jwtAccessTokenService(JwtProperties properties) {
        return new JwtAccessTokenService(properties.secretBytes(), properties.issuer());
    }

    @Bean
    public RefreshTokenService refreshTokenService() {
        return new RefreshTokenService();
    }

    @Bean
    public AuthFlowService authFlowService(AuthFlowService.AccountStore accounts,
            AuthFlowService.SessionStore sessions, AuthPolicyService policy,
            JwtAccessTokenService access, RefreshTokenService refresh, PasswordEncoder passwords) {
        return new AuthFlowService(accounts, sessions, policy, access, refresh, passwords);
    }

    /**
     * 사용자 이름과 비밀번호로 인증하는 방식은 쓰지 않는다. 이 빈이 없으면 Spring Boot 가
     * 임시 계정과 임시 비밀번호를 자동으로 만들어 로그에 출력하므로, 항상 실패하는 구현으로 막는다.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Form based authentication is not used");
        };
    }
}

package com.sfaasproject.stf.operations.config;

import com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore;
import com.sfaasproject.stf.operations.service.BTokenGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class BTokenGuardConfiguration {
    @Bean
    public BTokenGuard bTokenGuard(SessionStore sessions) {
        return new BTokenGuard(sessions);
    }
}

package com.sfaasproject.stf.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Redis 기반 폐기 저장소 대신 메모리 구현을 쓰는 테스트 설정. */
@TestConfiguration
public class AuthTestConfig {

    @Bean
    @Primary
    public InMemoryAccessRevocationStore inMemoryAccessRevocationStore() {
        return new InMemoryAccessRevocationStore();
    }
}

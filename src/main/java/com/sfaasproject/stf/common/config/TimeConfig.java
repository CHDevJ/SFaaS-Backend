package com.sfaasproject.stf.common.config;

import com.sfaasproject.stf.common.time.KstTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** 모든 서비스가 같은 시계를 쓰도록 하고, 테스트에서는 교체할 수 있게 빈으로 둔다. */
    @Bean
    public Clock clock() {
        return Clock.system(KstTime.KST);
    }
}

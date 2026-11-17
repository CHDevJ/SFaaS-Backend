package com.sfaasproject.stf.operations.config;

import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates fresh local-only login accounts without changing V2 seeds or existing passwords. */
@Configuration(proxyBeanMethods = false)
@Profile("manual-test")
public class ManualTestAccountConfiguration {
    @Bean
    public ApplicationRunner manualTestAccounts(JdbcTemplate jdbc, AccountRepository accounts,
            PasswordEncoder passwords, Clock clock, PlatformTransactionManager transactions,
            @Value("${MANUAL_LOGIN_PASSWORD}") String password) {
        return args -> {
            if (!"stf_b_manual".equals(jdbc.queryForObject("SELECT DATABASE()", String.class)))
                throw new IllegalStateException("Manual accounts require the dedicated stf_b_manual database");
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Map<String,String> roles=Map.of("badmin01","SYSTEM_ADMIN","bmaint01","MAINTENANCE","bline01","LINE_MANAGER");
                roles.forEach((login,role)-> {
                    if (accounts.existsByLoginId(login)) return;
                    var now=LocalDateTime.now(clock);
                    var account=AccountEntity.signup(login,passwords.encode(password),"[테스트] "+role,now);
                    account.approve(role,null,now);
                    accounts.save(account);
                });
            });
        };
    }
}

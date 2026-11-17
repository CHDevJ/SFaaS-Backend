package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sfaasproject.stf.operations.config.ManualTestAccountConfiguration;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

class ManualTestAccountConfigurationTest {
    @Test void refusesToCreateDemoAccountsInAnyOtherDatabase() {
        var jdbc=mock(JdbcTemplate.class);
        var accounts=mock(AccountRepository.class);
        var encoder=mock(PasswordEncoder.class);
        when(jdbc.queryForObject("SELECT DATABASE()",String.class)).thenReturn("shared_database");
        var runner=new ManualTestAccountConfiguration().manualTestAccounts(jdbc,accounts,encoder,
                Clock.systemUTC(),mock(PlatformTransactionManager.class),"test-only");
        assertThrows(IllegalStateException.class,()->runner.run(new DefaultApplicationArguments()));
        verifyNoInteractions(accounts,encoder);
    }
    @Test void existingDemoAccountsKeepTheirPasswordAndRole() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        var accounts=mock(AccountRepository.class);
        var encoder=mock(PasswordEncoder.class);
        when(jdbc.queryForObject("SELECT DATABASE()",String.class)).thenReturn("stf_b_manual");
        when(accounts.existsByLoginId(anyString())).thenReturn(true);
        new ManualTestAccountConfiguration().manualTestAccounts(jdbc,accounts,encoder,
                Clock.systemUTC(),mock(PlatformTransactionManager.class),"test-only")
                .run(new DefaultApplicationArguments());
        verify(accounts,never()).save(any()); verifyNoInteractions(encoder);
    }
}

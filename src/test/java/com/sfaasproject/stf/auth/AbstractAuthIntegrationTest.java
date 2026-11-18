package com.sfaasproject.stf.auth;

import com.sfaasproject.stf.auth.repository.AuthTokenRepository;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.system.repository.ChangeLogRepository;
import com.sfaasproject.stf.telegram.service.RedisTelegramLinkTokenStore;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.service.TemporaryPasswordSender;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증·계정 통합 테스트의 공통 기반. H2 메모리 DB 를 쓰고, Redis 는 메모리 구현과 목으로 바꾼다.
 * 같은 설정을 쓰는 하위 클래스끼리 스프링 컨텍스트를 공유한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthTestConfig.class)
public abstract class AbstractAuthIntegrationTest {

    protected static final String PASSWORD = "Passw0rd!x";

    @MockitoBean
    protected RedisTelegramLinkTokenStore telegramLinkTokenStore;

    /** 실제 텔레그램으로 보내지 않는다. 기본값은 발송 실패(false). */
    @MockitoBean
    protected TemporaryPasswordSender temporaryPasswordSender;

    @Autowired
    protected InMemoryTemporaryPasswordStore temporaryPasswords;
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected AccountRepository accounts;
    @Autowired
    protected AuthTokenRepository tokens;
    @Autowired
    protected ChangeLogRepository changeLogs;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtAccessTokenService jwt;
    @Autowired
    protected InMemoryAccessRevocationStore revocations;
    @Autowired
    protected Clock clock;

    @BeforeEach
    void cleanUp() {
        changeLogs.deleteAll();
        tokens.deleteAll();
        accounts.deleteAll();
        revocations.reset();
        temporaryPasswords.reset();
    }

    /** 승인된 계정을 만든다. role 은 ADMIN, USER, USER 중 하나. */
    protected AccountEntity approvedAccount(String loginId, String role) {
        LocalDateTime now = LocalDateTime.now(clock);
        AccountEntity account = AccountEntity.signup(loginId, passwordEncoder.encode(PASSWORD), "테스터", now);
        account.approve(role, null, now);
        return accounts.save(account);
    }

    /** 승인 대기 계정을 만든다. */
    protected AccountEntity pendingAccount(String loginId) {
        return accounts.save(AccountEntity.signup(loginId, passwordEncoder.encode(PASSWORD), "대기자",
                LocalDateTime.now(clock)));
    }

    protected String loginBody(String loginId, String password) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}";
    }

    /** 이 계정으로 로그인한 것과 같은 Authorization 헤더 값. */
    protected String bearerFor(AccountEntity account) {
        Permission position = Permission.valueOf(account.getRole());
        return "Bearer " + jwt.issue(account.getId(), position, clock.instant()).value();
    }
}

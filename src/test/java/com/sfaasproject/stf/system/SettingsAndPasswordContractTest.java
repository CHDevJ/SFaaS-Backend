package com.sfaasproject.stf.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.service.AccessRevocationStore;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.measurement.service.IngestionMessageGuard;
import com.sfaasproject.stf.operations.port.BPorts;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2026-11-18 계약 정리: SYS-002 오류코드(SETTING_OUT_OF_RANGE), SYS-003 ranges, 설정 변경 이력의 한글 이름,
 * AUTH-006 의 현재 비밀번호 오류 문구. SYS 는 B 설정(stf.backend-b.enabled)으로 켜지므로 B 통합 테스트와 같이 띄운다.
 */
@SpringBootTest(properties = {
    "stf.backend-b.enabled=true",
    "stf.backend-b.jobs-enabled=false",
    "stf.backend-b.ingestion-enabled=false",
    "stf.telegram.webhook-secret=contract-test-callback",
    "INTERNAL_API_KEY=contract-test-internal-key-0123456789",
    "spring.datasource.url=jdbc:h2:mem:settings-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
class SettingsAndPasswordContractTest {

    @MockitoBean AccessRevocationStore revocations;
    @MockitoBean TelegramDeliveryService delivery;
    @MockitoBean BPorts.LinkTokens tokens;
    @MockitoBean IngestionMessageGuard guard;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;

    AccountEntity admin;
    String adminToken;

    @BeforeEach
    void setUp() {
        for (String table : List.of("system_setting", "change_log", "auth_token", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        AccountEntity account = AccountEntity.signup("settingadmin", passwords.encode("Admin1234!"), "관리자", now);
        account.approve(Permission.ADMIN.name(), null, now);
        admin = accounts.save(account);
        adminToken = "Bearer " + jwt.issue(admin.getId(), Permission.ADMIN, clock.instant()).value();
    }

    @Test
    void judgementDangerNotBelowWarningUsesContractErrorCode() throws Exception {
        mvc.perform(put("/api/v1/system/settings/judgement").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"communicationLossSeconds\":30,\"alarmConsecutiveCount\":3,"
                                + "\"inventoryWarningMinutes\":30,\"inventoryDangerMinutes\":30}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SETTING_OUT_OF_RANGE"));
    }

    @Test
    void calculationSettingsIncludeInputRangesAndChangesAreLoggedWithKoreanNames() throws Exception {
        mvc.perform(get("/api/v1/system/settings/calculation").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranges.length()").value(3))
                .andExpect(jsonPath("$.data.ranges[0].field").value("unitPrice"))
                .andExpect(jsonPath("$.data.ranges[0].minimum").value(0))
                .andExpect(jsonPath("$.data.ranges[0].maximum").value(100000000))
                .andExpect(jsonPath("$.data.ranges[1].field").value("speedWindowMinutes"))
                .andExpect(jsonPath("$.data.ranges[1].minimum").value(5))
                .andExpect(jsonPath("$.data.ranges[2].field").value("hourlyProduction"))
                .andExpect(jsonPath("$.data.ranges[2].maximum").value(1000));

        mvc.perform(put("/api/v1/system/settings/calculation").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":50000,\"speedWindowMinutes\":10,\"hourlyProduction\":360}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranges.length()").value(3));
        List<String> logged = jdbc.queryForList("SELECT CONCAT(target_name, '|', field_name, '|', new_value)"
                + " FROM change_log WHERE target_type = 'SETTING' ORDER BY change_id", String.class);
        assertThat(logged).contains("계산 기준값|개당 금액(원)|50000");
        assertThat(logged).noneMatch(line -> line.contains("UNIT_PRICE") || line.contains("설정값"));
    }

    @Test
    void wrongCurrentPasswordKeepsCodeButSaysCurrentPassword() throws Exception {
        mvc.perform(put("/api/v1/auth/me/password").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong12345!\",\"newPassword\":\"Changed123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("현재 비밀번호를 확인해 주세요."));
    }
}

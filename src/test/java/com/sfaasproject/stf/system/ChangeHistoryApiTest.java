package com.sfaasproject.stf.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AuthTestConfig;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.telegram.service.RedisTelegramLinkTokenStore;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import com.sfaasproject.stf.user.service.TemporaryPasswordSender;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** AUD-001 설정 변경 이력. B 의 H2 스키마(V1 과 같은 컬럼)의 change_log 를 읽는다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:change-histories;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import(AuthTestConfig.class)
class ChangeHistoryApiTest {

    private static final String URL = "/api/v1/system/change-histories";
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 11, 18, 0, 0);

    @MockitoBean RedisTelegramLinkTokenStore telegramLinkTokenStore;
    @MockitoBean TemporaryPasswordSender temporaryPasswordSender;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;

    AccountEntity admin;
    AccountEntity user;
    String adminToken;
    String userToken;

    @BeforeEach
    void setUp() {
        for (String table : List.of("change_log", "auth_token", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        admin = approved("auditadmin", "관리자", Permission.ADMIN);
        user = approved("audituser", "작업자", Permission.USER);
        adminToken = bearer(admin, Permission.ADMIN);
        userToken = bearer(user, Permission.USER);
        log(DAY.plusHours(9), admin, "THRESHOLD", "OR-04-1/coolant_flow", "경보 기준", "10", "12");
        log(DAY.plusHours(10), user, "EQUIPMENT", "OR-04-1", "이름", "연삭기", "연삭기 1호기");
        log(DAY.plusHours(10), user, "LINE", "외륜", "표시순서", "1", "2");
        log(DAY.plusHours(11), admin, "SETTING", "판정 설정", "주의 기준(분)", "30", "100%_done");
        log(DAY.minusMinutes(1), admin, "LINE", "어제 라인", "라인 등록", null, "어제 라인");
        log(DAY.plusDays(1), admin, "LINE", "내일 0시", "라인 등록", null, "내일 0시");
    }

    @Test
    void listsChangesInPeriodNewestFirstWithChanger() throws Exception {
        mvc.perform(search(DAY, DAY.plusDays(1)).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].itemType").value("SETTING"))
                .andExpect(jsonPath("$.data[0].itemName").value("판정 설정"))
                .andExpect(jsonPath("$.data[0].fieldName").value("주의 기준(분)"))
                .andExpect(jsonPath("$.data[0].previousValue").value("30"))
                .andExpect(jsonPath("$.data[0].changedAt").value("2026-11-18T11:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].changer.userId").value(admin.getId()))
                .andExpect(jsonPath("$.data[0].changer.name").value("관리자"))
                .andExpect(jsonPath("$.data[0].changer.permission").value("ADMIN"))
                // 같은 시각이면 나중에 기록한 것부터.
                .andExpect(jsonPath("$.data[1].itemType").value("LINE"))
                .andExpect(jsonPath("$.data[2].itemType").value("EQUIPMENT"))
                .andExpect(jsonPath("$.data[2].changer.permission").value("USER"))
                .andExpect(jsonPath("$.data[3].itemType").value("THRESHOLD"))
                .andExpect(jsonPath("$.meta.totalElements").value(4));
    }

    @Test
    void filtersByItemTypeKeywordAndPages() throws Exception {
        mvc.perform(search(DAY, DAY.plusDays(1)).param("itemType", "LINE").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].itemName").value("외륜"));
        // 대상·항목·값에서 대소문자 없이 찾는다.
        mvc.perform(search(DAY, DAY.plusDays(1)).param("keyword", "COOLANT").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].itemType").value("THRESHOLD"));
        mvc.perform(search(DAY, DAY.plusDays(1)).param("keyword", "1호기").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.length()").value(1));
        // % 와 _ 는 글자 그대로 찾는다.
        mvc.perform(search(DAY, DAY.plusDays(1)).param("keyword", "%_").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].itemType").value("SETTING"));
        mvc.perform(search(DAY, DAY.plusDays(1)).param("keyword", "_").param("itemType", "LINE")
                        .header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(search(DAY, DAY.plusDays(1)).param("size", "2").param("page", "1")
                        .header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].itemType").value("EQUIPMENT"))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void validatesInputAndAllowsOnlyAdmin() throws Exception {
        mvc.perform(search(DAY, DAY).header("Authorization", adminToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_PERIOD"));
        for (String[] param : new String[][] {{"itemType", "PASSWORD"}, {"keyword", "가".repeat(101)},
                {"size", "101"}, {"page", "-1"}}) {
            mvc.perform(search(DAY, DAY.plusDays(1)).param(param[0], param[1]).header("Authorization", adminToken))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get(URL).param("from", "2026-11-18T00:00:00+09:00").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(search(DAY, DAY.plusDays(1)).header("Authorization", userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_PERMISSION_DENIED"));
        mvc.perform(search(DAY, DAY.plusDays(1))).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private MockHttpServletRequestBuilder search(LocalDateTime from, LocalDateTime to) {
        return get(URL).param("from", kst(from)).param("to", kst(to));
    }

    private static String kst(LocalDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "+09:00";
    }

    private AccountEntity approved(String loginId, String name, Permission permission) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        AccountEntity account = AccountEntity.signup(loginId, passwords.encode("Audit12345!"), name, now);
        account.approve(permission.name(), null, now);
        return accounts.save(account);
    }

    private String bearer(AccountEntity account, Permission permission) {
        return "Bearer " + jwt.issue(account.getId(), permission, clock.instant()).value();
    }

    private void log(LocalDateTime at, AccountEntity by, String type, String target, String field, String before,
            String after) {
        jdbc.update("INSERT INTO change_log(changed_at, changed_by, target_type, target_name, field_name, old_value,"
                + " new_value) VALUES (?, ?, ?, ?, ?, ?, ?)", at, by.getId(), type, target, field, before, after);
    }
}

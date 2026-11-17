package com.sfaasproject.stf.operations;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;

@SpringBootTest(
        properties = {
            "stf.backend-b.enabled=true",
            "stf.backend-b.jobs-enabled=false",
            "stf.backend-b.ingestion-enabled=true",
            "spring.jpa.hibernate.ddl-auto=none",
            "INTERNAL_API_KEY=0123456789abcdef0123456789abcdef",
            "spring.datasource.url=jdbc:h2:mem:b-spring;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
        })
class BackendBSpringTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock backendBClock;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.sfaasproject.stf.measurement.service.IngestionMessageGuard guard;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore sessions;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        org.mockito.Mockito.when(guard.acquire(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(
                        com.sfaasproject.stf.measurement.service.IngestionMessageGuard.Acquisition
                                .ACQUIRED);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM account");
        jdbc.update(
                "INSERT INTO account(account_id,login_id,name,role,approval_status) VALUES"
                        + " (1,'manager','라인','LINE_MANAGER','APPROVED')");
    }

    String token() {
        return jwt.issue(1, Position.LINE_MANAGER, backendBClock.instant()).value();
    }

    @Test
    void realSecurityChainRequiresBearerAndUsesAccountRole() throws Exception {
        mvc.perform(get("/api/v1/inventory")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(
                        get("/api/v1/system/telegram/deliveries")
                                .header("Authorization", "Bearer " + token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualQueryValidationRejectsNegativeIdsAndReversedPeriod() throws Exception {
        mvc.perform(get("/api/v1/alarms/-1").header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/v1/inventory/history")
                                .header("Authorization", "Bearer " + token())
                                .param("from", "2026-11-18T00:00:00+09:00")
                                .param("to", "2026-11-17T00:00:00+09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PERIOD"));
        mvc.perform(
                        get("/api/v1/alarms")
                                .header("Authorization", "Bearer " + token())
                                .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalEndpointIsNeverAnonymousAndRequiresInternalKey() throws Exception {
        String body =
                "{\"messageId\":\"x\",\"equipmentCode\":\"OR-01-1\",\"measuredAt\":\"2026-11-17T09:00:00+09:00\",\"values\":[{\"dataItemCode\":\"temperature\",\"valueType\":\"MEASUREMENT\",\"value\":25}]}";
        mvc.perform(post("/internal/v1/measurements").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/internal/v1/measurements")
                                .header("X-Internal-Api-Key", "wrong")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/internal/v1/measurements")
                                .header("X-Internal-Api-Key", "0123456789abcdef0123456789abcdef")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EQUIPMENT_NOT_FOUND"));
    }

    @Test
    void deletedAccountTokenCannotAccessBackend() throws Exception {
        String token = token();
        jdbc.update("UPDATE account SET deleted_at=CURRENT_TIMESTAMP WHERE account_id=1");
        mvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedJwtCannotUseHttpApi() throws Exception {
        org.mockito.Mockito.when(sessions.isAccessRevoked(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        mvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer " + token()))
                .andExpect(status().isUnauthorized());
    }
}

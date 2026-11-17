package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sfaasproject.stf.auth.service.AccessRevocationStore;
import com.sfaasproject.stf.operations.service.BWebSocketSessions;
import jakarta.servlet.http.Cookie;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.operations.event.JdbcBEventOutbox;
import com.sfaasproject.stf.operations.event.SignupNotificationRequested;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService.DeliveryResult;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.entity.ApprovalStatus;
import com.sfaasproject.stf.user.event.SignupDecidedEvent;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
    "stf.backend-b.enabled=true", "stf.backend-b.legacy-v5-enabled=true", "stf.backend-b.jobs-enabled=false",
    "spring.datasource.url=jdbc:h2:mem:b-auth-integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
class BackendAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;
    @Autowired BWebSocketSessions webSockets;
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcBEventOutbox outbox;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean AccessRevocationStore revocations;
    @MockitoBean TelegramDeliveryService delivery;
    final String password = "TestOnly123!";
    AccountEntity admin;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM backend_event_outbox");
        jdbc.update("DELETE FROM backend_event_claim");
        jdbc.update("DELETE FROM notification_log");
        jdbc.update("DELETE FROM change_log");
        jdbc.update("DELETE FROM auth_token");
        jdbc.update("DELETE FROM duty_member");
        jdbc.update("DELETE FROM duty");
        jdbc.update("DELETE FROM account");
        admin = approved("adminauth", Permission.ADMIN);
        when(delivery.sendWithOneRetry(anyString(),anyString())).thenReturn(new DeliveryResult(true,0,null));
    }

    AccountEntity approved(String login, Permission role) {
        var entity=AccountEntity.signup(login,passwords.encode(password),"통합 검증",LocalDateTime.now(clock));
        entity.approve(role.name(),null,LocalDateTime.now(clock));
        return accounts.save(entity);
    }
    AccountEntity pending(boolean connected) {
        var entity=AccountEntity.signup("pendingauth",passwords.encode(password),"가입 신청자",LocalDateTime.now(clock));
        if(connected) entity.connectTelegram("test-private-chat",LocalDateTime.now(clock));
        return accounts.save(entity);
    }
    String bearer(AccountEntity user) {
        return "Bearer "+jwt.issue(user.getId(),Permission.valueOf(user.getRole()),clock.instant()).value();
    }
    String decisionPath(AccountEntity user,String action) {
        return "/api/v1/system/signup-requests/"+user.getId()+"/"+action;
    }
    @Test void oneSecurityChainJwtAndClockServeBothDevelopers() {
        assertEquals(1,context.getBeansOfType(SecurityFilterChain.class).size());
        assertEquals(1,context.getBeansOfType(JwtAccessTokenService.class).size());
        assertEquals(1,context.getBeansOfType(Clock.class).size());
    }
    @Test void loginJwtCallsBackendBWithClaimsPrincipalAndPersistsSession() throws Exception {
        String response=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"loginId\":\"adminauth\",\"password\":\""+password+"\"}"))
                .andExpect(status().isOk()).andExpect(header().exists("Set-Cookie"))
                .andReturn().getResponse().getContentAsString();
        var responseBody=JsonMapper.builder().build().readValue(response, java.util.Map.class);
        String token=((java.util.Map<?,?>)responseBody.get("data")).get("accessToken").toString();
        mvc.perform(get("/api/v1/inventory").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM auth_token",Integer.class));
        assertNotNull(accounts.findById(admin.getId()).orElseThrow().getLastLoginAt());
    }
    @Test void legacySystemDutyHasNoV7UserExemption() throws Exception {
        var maint=approved("maintauth",Permission.USER);
        mvc.perform(get("/api/v1/system/alarm-duty-schedules").header("Authorization",bearer(maint)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/system/alarm-duty-schedules").header("Authorization",bearer(maint))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("AUTH_PERMISSION_DENIED"));
    }
    @Test void latestDatabaseRoleOverridesOldAdminClaims() throws Exception {
        String token=bearer(admin);
        jdbc.update("UPDATE account SET role='USER' WHERE account_id=?",admin.getId());
        mvc.perform(get("/api/v1/system/telegram/deliveries").header("Authorization",token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inventory").header("Authorization",token)).andExpect(status().isOk());
    }
    @Test void approvalDeliversOnlyAfterCommitAndCreatesDeliveryLog() throws Exception {
        var applicant=pending(true);
        when(delivery.sendWithOneRetry(anyString(),anyString())).thenAnswer(invocation -> {
            assertEquals(ApprovalStatus.APPROVED,accounts.findById(applicant.getId()).orElseThrow().getApprovalStatus());
            return new DeliveryResult(true,0,null);
        });
        mvc.perform(post(decisionPath(applicant,"approval")).header("Authorization",bearer(admin))
                .contentType("application/json").content("{\"permission\":\"USER\"}"))
                .andExpect(status().isOk());
        verify(delivery).sendWithOneRetry(eq("test-private-chat"),contains("승인"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification_log WHERE notify_type='SIGNUP_RESULT'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM backend_event_outbox WHERE published_at IS NULL",Integer.class));
    }
    @Test void rejectionKeepsSnapshotAfterApplicantIsDeleted() throws Exception {
        var applicant=pending(true);
        mvc.perform(post(decisionPath(applicant,"rejection")).header("Authorization",bearer(admin))
                .contentType("application/json").content("{\"reason\":\"테스트 거절 사유\"}"))
                .andExpect(status().isNoContent());
        assertFalse(accounts.existsById(applicant.getId()));
        var row=jdbc.queryForMap("SELECT account_id,receiver_name,message FROM notification_log");
        assertNull(row.get("account_id")); assertEquals("가입 신청자",row.get("receiver_name"));
        assertTrue(row.get("message").toString().contains("테스트 거절 사유"));
    }
    @Test void rolledBackDecisionLeavesNeitherOutboxNorNotification() {
        var applicant=pending(true);
        assertThrows(IllegalStateException.class,()-> new TransactionTemplate(transactions).execute(status->{
            events.publishEvent(new SignupDecidedEvent(SignupDecidedEvent.Outcome.APPROVED,
                    applicant.getId(),applicant.getLoginId(),applicant.getName(),"USER","test-private-chat",null));
            throw new IllegalStateException("rollback");
        }));
        verifyNoInteractions(delivery);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM backend_event_outbox",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification_log",Integer.class));
    }
    @Test void retryOfPublishedDecisionDoesNotSendTwice() throws Exception {
        var applicant=pending(true);
        mvc.perform(post(decisionPath(applicant,"approval")).header("Authorization",bearer(admin))
                .contentType("application/json").content("{\"permission\":\"USER\"}"))
                .andExpect(status().isOk());
        long id=jdbc.queryForObject("SELECT outbox_id FROM backend_event_outbox",Long.class);
        jdbc.update("UPDATE backend_event_outbox SET published_at=NULL WHERE outbox_id=?",id);
        outbox.dispatch(id);
        verify(delivery,times(1)).sendWithOneRetry(anyString(),anyString());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification_log",Integer.class));
    }
    @Test void deliveryFailureDoesNotUndoApprovedAccount() throws Exception {
        var applicant=pending(true);
        when(delivery.sendWithOneRetry(anyString(),anyString())).thenReturn(new DeliveryResult(false,1,"연결 확인 필요"));
        mvc.perform(post(decisionPath(applicant,"approval")).header("Authorization",bearer(admin))
                .contentType("application/json").content("{\"permission\":\"USER\"}"))
                .andExpect(status().isOk());
        assertEquals(ApprovalStatus.APPROVED,accounts.findById(applicant.getId()).orElseThrow().getApprovalStatus());
        assertEquals("FAILURE",jdbc.queryForObject("SELECT send_result FROM notification_log",String.class));
    }
    @Test void unconnectedApplicantNeedsNoDeliveryOrOutbox() throws Exception {
        var applicant=pending(false);
        mvc.perform(post(decisionPath(applicant,"approval")).header("Authorization",bearer(admin))
                .contentType("application/json").content("{\"permission\":\"USER\"}"))
                .andExpect(status().isOk());
        verifyNoInteractions(delivery);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM backend_event_outbox",Integer.class));
    }
    @Test void sessionEndpointsEnforceTheirOwnAuthenticationContracts() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID"));
    }

    @Test void refreshedTokenCallsBackendBAndLogoutRevokesHttpAndExistingWebSocket() throws Exception {
        var login = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"loginId\":\"adminauth\",\"password\":\""+password+"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String cookie = login.getHeader("Set-Cookie").split(";", 2)[0].substring("stfRefreshToken=".length());
        var refresh = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("stfRefreshToken", cookie)))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body = JsonMapper.builder().build().readValue(refresh.getContentAsString(), java.util.Map.class);
        String token = ((java.util.Map<?,?>)body.get("data")).get("accessToken").toString();
        var claims = jwt.verify(token, clock.instant());
        String nextCookie = refresh.getHeader("Set-Cookie").split(";", 2)[0].substring("stfRefreshToken=".length());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(admin.getId()));
        mvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer "+token)).andExpect(status().isOk());
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("b-session-integration");
        when(socket.isOpen()).thenReturn(true);
        webSockets.opened(socket);
        webSockets.authenticated(socket.getId(), claims);
        assertTrue(webSockets.permitDelivery(socket.getId()));
        doAnswer(invocation -> {
            when(revocations.isAccessRevoked(claims.jti())).thenReturn(true);
            return null;
        }).when(revocations).revokeAccess(eq(claims.jti()), any());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer "+token)
                .cookie(new Cookie("stfRefreshToken", nextCookie))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer "+token))
                .andExpect(status().isUnauthorized());
        assertFalse(webSockets.permitDelivery(socket.getId()));
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("stfRefreshToken", nextCookie)))
                .andExpect(status().isUnauthorized());
    }
    @Test void notificationEnvelopeDoesNotPrintChatOrMessage() {
        assertEquals("SignupNotificationRequested[redacted]",
                new SignupNotificationRequested("e",1L,"name","chat","message").toString());
    }
}

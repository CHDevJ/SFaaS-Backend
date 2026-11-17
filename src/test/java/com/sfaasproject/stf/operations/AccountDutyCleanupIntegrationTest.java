package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sfaasproject.stf.auth.service.AccessRevocationStore;
import com.sfaasproject.stf.auth.service.TemporaryPasswordStore;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.operations.service.BWebSocketSessions;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.event.AccountDeletedEvent;
import com.sfaasproject.stf.user.repository.AccountRepository;
import com.sfaasproject.stf.user.service.TemporaryPasswordSender;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@SpringBootTest(properties = {
    "stf.backend-b.enabled=true", "stf.backend-b.jobs-enabled=false",
    "spring.datasource.url=jdbc:h2:mem:b-account-duty-cleanup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
class AccountDutyCleanupIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcBDatabase db;
    @Autowired BWebSocketSessions sockets;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean AccessRevocationStore revocations;
    @MockitoBean TemporaryPasswordStore temporaryPasswords;
    @MockitoBean TemporaryPasswordSender sender;
    @MockitoSpyBean AuditRecorder audit;
    AccountEntity admin;
    AccountEntity target;
    AccountEntity replacement;
    LocalDate today;

    @BeforeEach void setup() {
        for (String table : new String[]{"backend_event_outbox", "backend_event_claim", "change_log",
                "auth_token", "duty_member", "duty", "account"}) jdbc.update("DELETE FROM " + table);
        today=LocalDate.now(clock);
        admin=approved("cleanupadmin", Position.SYSTEM_ADMIN);
        target=approved("cleanuptarget", Position.MAINTENANCE);
        replacement=approved("cleanupother", Position.MAINTENANCE);
    }

    AccountEntity approved(String login, Position role) {
        var user=AccountEntity.signup(login,passwords.encode("CleanupTest123!"),login,LocalDateTime.now(clock));
        user.approve(role.name(),null,LocalDateTime.now(clock));
        return accounts.save(user);
    }
    String bearer(AccountEntity user) {
        return "Bearer "+jwt.issue(user.getId(),Position.valueOf(user.getRole()),clock.instant()).value();
    }
    String deletePath() { return "/api/v1/system/users/"+target.getId(); }
    long period(LocalDate from, LocalDate to, AccountEntity... members) {
        long id=db.insert("INSERT INTO duty(start_date,end_date) VALUES (?,?)",from,to);
        for(var member:members) jdbc.update("INSERT INTO duty_member(duty_id,account_id) VALUES (?,?)",id,member.getId());
        return id;
    }
    int members(long period,long account) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM duty_member WHERE duty_id=? AND account_id=?",Integer.class,period,account);
    }

    @Test void onlyAssigneeOfCurrentPeriodCannotBeDeletedAndNothingChanges() throws Exception {
        long id=period(today.minusDays(2),today,target);
        String originalHash=target.getPasswordHash();
        mvc.perform(delete(deletePath()).header("Authorization",bearer(admin)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("DUTY_REPLACEMENT_REQUIRED"));
        var unchanged=accounts.findById(target.getId()).orElseThrow();
        assertFalse(unchanged.isDeleted());
        assertEquals(originalHash,unchanged.getPasswordHash());
        assertEquals(1,members(id,target.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM change_log",Integer.class));
    }

    @Test void futurePeriodWithoutReplacementBlocksTheEntireDeletion() throws Exception {
        long current=period(today.minusDays(2),today,target,replacement);
        long future=period(today.plusDays(1),today.plusDays(5),target);
        mvc.perform(delete(deletePath()).header("Authorization",bearer(admin)))
                .andExpect(status().is(422));
        assertFalse(accounts.findById(target.getId()).orElseThrow().isDeleted());
        assertEquals(1,members(current,target.getId()));
        assertEquals(1,members(future,target.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM change_log",Integer.class));
    }

    @Test void successfulDeletionRemovesCurrentAndFutureOnlyPreservingPeriodsAndHistory() throws Exception {
        long past=period(today.minusDays(5),today.minusDays(3),target);
        long current=period(today.minusDays(2),today,target,replacement);
        long future=period(today.plusDays(1),today.plusDays(5),target,replacement);
        mvc.perform(delete(deletePath()).header("Authorization",bearer(admin))).andExpect(status().isNoContent());
        assertTrue(accounts.findById(target.getId()).orElseThrow().isDeleted());
        assertEquals(1,members(past,target.getId()));
        for(long id:new long[]{current,future}) {
            assertEquals(0,members(id,target.getId()));
            assertEquals(1,members(id,replacement.getId()));
        }
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM duty",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM change_log WHERE target_type='DUTY'",Integer.class));
        mvc.perform(get("/api/v1/system/alarm-duty-schedules/current").header("Authorization",bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dutyUser.userId").value(replacement.getId()));
        mvc.perform(get("/api/v1/alarm-duty-schedules/calendar").param("month",today.toString().substring(0,7))
                .header("Authorization",bearer(admin))).andExpect(status().isOk());
    }

    @Test void reassigningThroughExistingDutyApiThenDeletingKeepsHistoricalCalendarReadable() throws Exception {
        long past=period(today.minusDays(4),today.minusDays(1),target);
        long current=period(today,today.plusDays(3),target);
        String token=bearer(admin);
        mvc.perform(put("/api/v1/system/alarm-duty-schedules/"+current).header("Authorization",token)
                .contentType("application/json").content("{\"startDate\":\""+today+"\",\"endDate\":\""+today.plusDays(3)
                        +"\",\"dutyUserId\":"+replacement.getId()+"}"))
                .andExpect(status().isOk());
        mvc.perform(delete(deletePath()).header("Authorization",token)).andExpect(status().isNoContent());
        assertEquals(1,members(past,target.getId()));
        mvc.perform(get("/api/v1/alarm-duty-schedules/calendar").param("month",today.toString().substring(0,7))
                .header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/system/alarm-duty-schedules").header("Authorization",token)
                .contentType("application/json").content("{\"startDate\":\""+today.plusDays(4)+"\",\"endDate\":\""
                        +today.plusDays(8)+"\",\"dutyUserId\":"+replacement.getId()+"}"))
                .andExpect(status().isCreated());
    }

    @Test void nonMaintenanceOrDeletedReplacementDoesNotPermitDeletion() throws Exception {
        long current=period(today,today.plusDays(2),target,replacement);
        jdbc.update("UPDATE account SET role='LINE_MANAGER' WHERE account_id=?",replacement.getId());
        mvc.perform(delete(deletePath()).header("Authorization",bearer(admin)))
                .andExpect(status().is(422));
        jdbc.update("UPDATE account SET role='MAINTENANCE',deleted_at=? WHERE account_id=?",LocalDateTime.now(clock),replacement.getId());
        mvc.perform(delete(deletePath()).header("Authorization",bearer(admin)))
                .andExpect(status().is(422));
        assertEquals(1,members(current,target.getId()));
    }

    @Test void auditFailureRollsBackLogicalDeletionAndMembershipCleanup() throws Exception {
        long current=period(today,today.plusDays(2),target,replacement);
        AuditRecorder auditTarget=AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("duty audit unavailable")).when(auditTarget)
                .record(anyLong(),eq(AuditTargetType.DUTY),anyString(),anyString(),any(),isNull());
        mvc.perform(delete(deletePath()).header("Authorization",bearer(admin)))
                .andExpect(status().isInternalServerError());
        assertFalse(accounts.findById(target.getId()).orElseThrow().isDeleted());
        assertEquals(1,members(current,target.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM change_log",Integer.class));
    }

    @Test void deletionInvalidatesExistingBHttpAndWebSocketAccess() throws Exception {
        String userToken=bearer(target);
        var claims=jwt.verify(userToken.substring(7),clock.instant());
        var socket=mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("deleted-account-duty-test"); when(socket.isOpen()).thenReturn(true);
        sockets.opened(socket); sockets.authenticated(socket.getId(),claims);
        assertTrue(sockets.permitDelivery(socket.getId()));
        mvc.perform(delete(deletePath()).header("Authorization",bearer(admin))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/inventory").header("Authorization",userToken)).andExpect(status().isUnauthorized());
        assertFalse(sockets.permitDelivery(socket.getId()));
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void repeatedEventDoesNotDuplicateCleanupAudits() throws Exception {
        period(today,today.plusDays(2),target,replacement);
        mvc.perform(delete(deletePath()).header("Authorization",bearer(admin))).andExpect(status().isNoContent());
        int logs=jdbc.queryForObject("SELECT COUNT(*) FROM change_log",Integer.class);
        new TransactionTemplate(transactions).execute(status -> {
            events.publishEvent(new AccountDeletedEvent(target.getId(),target.getLoginId(),"MAINTENANCE",admin.getId()));
            return null;
        });
        assertEquals(logs,jdbc.queryForObject("SELECT COUNT(*) FROM change_log",Integer.class));
    }

    @Test void eventWithoutTheAccountTransactionIsRejected() {
        assertThrows(RuntimeException.class,()->events.publishEvent(
                new AccountDeletedEvent(target.getId(),target.getLoginId(),"MAINTENANCE",admin.getId())));
        assertFalse(accounts.findById(target.getId()).orElseThrow().isDeleted());
    }
}

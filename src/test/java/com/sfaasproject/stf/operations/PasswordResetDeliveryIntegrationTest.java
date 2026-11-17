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
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService.DeliveryResult;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import com.sfaasproject.stf.user.service.TemporaryPasswordSender;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "stf.backend-b.enabled=true", "stf.backend-b.jobs-enabled=false",
    "spring.datasource.url=jdbc:h2:mem:b-password-delivery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class PasswordResetDeliveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher events;
    @Autowired JdbcBEventOutbox outbox;
    @Autowired TemporaryPasswordSender sender;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean AccessRevocationStore revocations;
    @MockitoBean TemporaryPasswordStore temporaryPasswords;
    @MockitoBean TelegramDeliveryService delivery;
    @MockitoSpyBean AuditRecorder audit;
    AccountEntity admin;
    AccountEntity target;
    final AtomicReference<String> transmitted = new AtomicReference<>();

    @BeforeEach void setup() {
        for(String table:new String[]{"backend_event_outbox","backend_event_claim","notification_log",
                "change_log","auth_token","duty_member","duty","account"}) jdbc.update("DELETE FROM "+table);
        admin=approved("deliveryadmin",Position.SYSTEM_ADMIN);
        target=approved("deliverytarget",Position.MAINTENANCE);
        target.connectTelegram("test-delivery-private-chat",LocalDateTime.now(clock)); accounts.save(target);
        transmitted.set(null);
        provider(new DeliveryResult(true,0,null));
    }
    AccountEntity approved(String login, Position role) {
        var account=AccountEntity.signup(login,passwords.encode("DeliveryTest123!"),login,LocalDateTime.now(clock));
        account.approve(role.name(),null,LocalDateTime.now(clock)); return accounts.save(account);
    }
    void provider(DeliveryResult result) {
        when(delivery.sendWithOneRetry(anyString(),anyString())).thenAnswer(call -> {
            transmitted.set(call.getArgument(1)); return result;
        });
    }
    String bearer(AccountEntity user) {
        return "Bearer "+jwt.issue(user.getId(),Position.valueOf(user.getRole()),clock.instant()).value();
    }
    String path() { return "/api/v1/system/users/"+target.getId()+"/password-reset"; }
    int logs() { return jdbc.queryForObject("SELECT COUNT(*) FROM notification_log",Integer.class); }
    String temporary() { return transmitted.get().split("임시 비밀번호: ")[1].split("\n")[0]; }
    void assertRedacted(CapturedOutput output) {
        String password=temporary();
        assertFalse(jdbc.queryForList("SELECT * FROM notification_log").toString().contains(password));
        assertFalse(jdbc.queryForList("SELECT * FROM backend_event_outbox").toString().contains(password));
        assertFalse(jdbc.queryForList("SELECT * FROM change_log").toString().contains(password));
        assertFalse(output.getAll().contains(password));
        assertEquals(PasswordResetDeliveryHistory.REDACTED_MESSAGE,jdbc.queryForObject("SELECT message FROM notification_log",String.class));
        String payload=jdbc.queryForObject("SELECT payload FROM backend_event_outbox",String.class);
        assertFalse(payload.contains("test-delivery-private-chat")); assertFalse(payload.contains("failureReason"));
    }

    @Test void successfulResetWritesRedactedHistoryAndHashOnly(CapturedOutput output) throws Exception {
        mvc.perform(post(path()).header("Authorization",bearer(admin))).andExpect(status().isNoContent());
        assertEquals(1,logs());
        var changed=accounts.findById(target.getId()).orElseThrow();
        assertTrue(changed.isMustChangePassword()); assertTrue(passwords.matches(temporary(),changed.getPasswordHash()));
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT send_result FROM notification_log",String.class));
        assertEquals("PASSWORD_RESET",jdbc.queryForObject("SELECT notify_type FROM notification_log",String.class));
        assertEquals(target.getName(),jdbc.queryForObject("SELECT receiver_name FROM notification_log",String.class));
        verify(temporaryPasswords).issued(eq(target.getId()),any()); assertRedacted(output);
        mvc.perform(get("/api/v1/system/telegram/deliveries").header("Authorization",bearer(admin))
                .param("date",LocalDate.now(clock).toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalCount").value(1));
    }
    @Test void failedDeliveryRemainsInHistoryDespitePasswordRollback(CapturedOutput output) throws Exception {
        provider(new DeliveryResult(false,1,"untrusted provider detail"));
        mvc.perform(post(path()).header("Authorization",bearer(admin))).andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("TELEGRAM_DELIVERY_FAILED"));
        assertEquals(target.getPasswordHash(),accounts.findById(target.getId()).orElseThrow().getPasswordHash());
        assertFalse(accounts.findById(target.getId()).orElseThrow().isMustChangePassword());
        assertEquals(1,logs()); assertEquals("FAILURE",jdbc.queryForObject("SELECT send_result FROM notification_log",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT retry_count FROM notification_log",Integer.class));
        assertEquals(PasswordResetDeliveryHistory.FAILURE_REASON,jdbc.queryForObject("SELECT fail_reason FROM notification_log",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM change_log",Integer.class));
        verifyNoInteractions(temporaryPasswords); assertRedacted(output);
    }
    @Test void successfulRetryHasOneHistoryRowAndCorrectRetryCount(CapturedOutput output) throws Exception {
        provider(new DeliveryResult(true,1,null));
        mvc.perform(post(path()).header("Authorization",bearer(admin))).andExpect(status().isNoContent());
        assertEquals(1,logs()); assertEquals(1,jdbc.queryForObject("SELECT retry_count FROM notification_log",Integer.class));
        assertRedacted(output);
    }
    @Test void providerExceptionContainingPasswordIsSanitized(CapturedOutput output) throws Exception {
        when(delivery.sendWithOneRetry(anyString(),anyString())).thenAnswer(call -> {
            transmitted.set(call.getArgument(1)); throw new IllegalStateException(transmitted.get()+" test-private-bot-value");
        });
        mvc.perform(post(path()).header("Authorization",bearer(admin))).andExpect(status().is(422));
        assertEquals(1,logs()); assertRedacted(output); assertFalse(output.getAll().contains("test-private-bot-value"));
    }
    @Test void failedResetAfterSuccessfulSendKeepsActualDeliverySuccess(CapturedOutput output) throws Exception {
        AuditRecorder recorder=AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("audit unavailable")).when(recorder).record(anyLong(),eq(AuditTargetType.ACCOUNT),anyString(),anyString(),any(),any());
        mvc.perform(post(path()).header("Authorization",bearer(admin))).andExpect(status().isInternalServerError());
        assertEquals(target.getPasswordHash(),accounts.findById(target.getId()).orElseThrow().getPasswordHash());
        assertEquals(1,logs()); assertEquals("SUCCESS",jdbc.queryForObject("SELECT send_result FROM notification_log",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM change_log",Integer.class)); assertRedacted(output);
    }
    @Test void unconnectedAccountDoesNotCreateAnAttemptOrChangePassword() throws Exception {
        jdbc.update("UPDATE account SET telegram_chat_id=NULL WHERE account_id=?",target.getId());
        mvc.perform(post(path()).header("Authorization",bearer(admin))).andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("TELEGRAM_NOT_CONNECTED"));
        verifyNoInteractions(delivery); assertEquals(0,logs());
    }
    @Test void deliveryLogRetryDoesNotResendPasswordOrDuplicateHistory(CapturedOutput output) throws Exception {
        jdbc.execute("ALTER TABLE notification_log ADD CONSTRAINT reset_log_failure CHECK (notify_type <> 'PASSWORD_RESET')");
        try {
            mvc.perform(post(path()).header("Authorization",bearer(admin))).andExpect(status().isNoContent());
            assertEquals(0,logs());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM backend_event_outbox WHERE published_at IS NULL",Integer.class));
        } finally { jdbc.execute("ALTER TABLE notification_log DROP CONSTRAINT reset_log_failure"); }
        outbox.retryPending(); outbox.retryPending();
        assertEquals(1,logs()); verify(delivery,times(1)).sendWithOneRetry(anyString(),anyString());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM backend_event_outbox WHERE published_at IS NULL",Integer.class));
        assertRedacted(output);
    }
    @Test void duplicateSafeEventIsIgnoredAndReceiverSnapshotSurvivesRename() {
        var result=new PasswordResetDeliveryRecorded("redacted-delivery-test",target.getId(),"발송 당시 이름",true,0,OffsetDateTime.now(clock));
        var tx=new TransactionTemplate(transactions);
        tx.execute(status -> { events.publishEvent(result); return null; });
        jdbc.update("UPDATE account SET name='변경 이름',deleted_at=CURRENT_TIMESTAMP WHERE account_id=?",target.getId());
        tx.execute(status -> { events.publishEvent(result); return null; });
        assertEquals(1,logs()); assertEquals("발송 당시 이름",jdbc.queryForObject("SELECT receiver_name FROM notification_log",String.class));
    }
    @Test void senderOutsideResetTransactionIsRejectedWithoutSending() {
        assertThrows(IllegalStateException.class,()->sender.send(target.getTelegramChatId(),"never transmitted"));
        verifyNoInteractions(delivery); assertEquals(0,logs());
    }
}

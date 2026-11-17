package com.sfaasproject.stf.operations.config;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowDecisionService;
import com.sfaasproject.stf.duty.service.DutyScheduleDecisionService;
import com.sfaasproject.stf.operations.adapter.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.scheduler.*;
import com.sfaasproject.stf.operations.service.*;
import com.sfaasproject.stf.telegram.service.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.function.Supplier;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
@EnableScheduling
@EnableTransactionManagement
public class BackendBConfiguration {
    @Bean(name = "taskScheduler")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler bJobScheduler() {
        var s = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        s.setPoolSize(4);
        s.setThreadNamePrefix("stf-jobs-");
        return s;
    }

    @Bean
    @ConditionalOnProperty(name = "stf.telegram.polling-enabled", havingValue = "true")
    public TelegramPollingJob bPolling(
            TelegramApplicationService service,
            EventClaims claims,
            UnitOfWork tx,
            @Value("${stf.telegram.bot-token}") String token) {
        return new TelegramPollingJob(TelegramPollingJob.http(token), service, claims, tx);
    }

    @Bean
    @ConditionalOnProperty(name = "stf.backend-b.ingestion-enabled", havingValue = "true")
    public com.sfaasproject.stf.measurement.security.InternalApiKeyVerifier bInternalKey(
            @Value("${INTERNAL_API_KEY}") String key) {
        return new com.sfaasproject.stf.measurement.security.InternalApiKeyVerifier(key);
    }

    @Bean
    @ConditionalOnProperty(name = "stf.backend-b.ingestion-enabled", havingValue = "true")
    public BIngestionApplicationService bIngestion(
            JdbcBDatabase db,
            BEventPublisher events,
            AlarmApplicationService commands,
            UnitOfWork tx,
            Clock backendBClock,
            com.sfaasproject.stf.measurement.service.IngestionMessageGuard guard,
            com.sfaasproject.stf.measurement.service.IngestionValidationService validator,
            com.sfaasproject.stf.measurement.service.ProductionCounterService counters,
            com.sfaasproject.stf.alarm.service.AlarmDecisionService rules,
            com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService status,
            Settings settings) {
        var store = new JdbcIngestionStore(db, events, commands);
        var processor =
                new com.sfaasproject.stf.measurement.service.IngestionProcessor(
                        store,
                        guard,
                        validator,
                        counters,
                        rules,
                        status,
                        () -> settings.integer("ALARM_STREAK_COUNT", 3),
                        () -> settings.integer("NORMAL_STREAK_COUNT", 3),
                        () -> Duration.ofSeconds(settings.integer("COMM_TIMEOUT_SEC", 30)));
        return new BIngestionApplicationService(processor, tx, backendBClock);
    }

    @Bean
    public JdbcBDatabase bDatabase(
            JdbcTemplate jdbc, PlatformTransactionManager manager, Clock backendBClock) {
        return new JdbcBDatabase(jdbc, new TransactionTemplate(manager), backendBClock);
    }

    @Bean
    public UnitOfWork bTransactions(PlatformTransactionManager manager) {
        return new UnitOfWork() {
            private final TransactionTemplate tx = new TransactionTemplate(manager);

            public <T> T execute(Supplier<T> work) {
                return tx.execute(status -> work.get());
            }
        };
    }

    @Bean
    public JdbcBUsersSettings bUsers(JdbcBDatabase db) {
        return new JdbcBUsersSettings(db);
    }

    @Bean
    public JdbcBClaims bClaims(JdbcBDatabase db) {
        return new JdbcBClaims(db);
    }

    @Bean
    public JdbcBDuties bDuties(JdbcBDatabase db, EventClaims claims) {
        return new JdbcBDuties(db, claims);
    }

    @Bean
    public JdbcBParts bParts(JdbcBDatabase db) {
        return new JdbcBParts(db);
    }

    @Bean
    public JdbcBAlarms bAlarms(JdbcBDatabase db) {
        return new JdbcBAlarms(db);
    }

    @Bean
    public JdbcBTelegram bTelegram(JdbcBDatabase db, Users users) {
        return new JdbcBTelegram(db, users);
    }

    @Bean
    public JdbcBInventory bInventory(JdbcBDatabase db) {
        return new JdbcBInventory(db);
    }

    @Bean
    public BReadPort bReadPort(
            JdbcBDatabase db,
            Users users,
            Settings settings,
            InventoryRuntime inventory,
            Alarms alarms) {
        return new JdbcBReadPort(db, users, settings, inventory, alarms);
    }

    @Bean
    public SignupNotificationBridge bSignupNotifications(BEventPublisher events, EventClaims claims,
            com.sfaasproject.stf.telegram.port.NotificationPort notifications) {
        return new SignupNotificationBridge(events, claims, notifications);
    }

    @Bean
    public BReadService bReads(BReadPort port) {
        return new BReadService(port);
    }

    @Bean
    public JdbcBEventOutbox bOutbox(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            ApplicationEventPublisher publisher,
            Clock backendBClock) {
        return new JdbcBEventOutbox(jdbc, manager, publisher, backendBClock);
    }

    @Bean
    public BEventPublisher bEvents(ApplicationEventPublisher publisher, JdbcBEventOutbox outbox) {
        return new BEventPublisher(publisher, outbox);
    }

    @Bean
    public PartApplicationService bPartCommands(Parts parts, Users users, UnitOfWork tx) {
        return new PartApplicationService(parts, users, tx);
    }

    @Bean
    public AlarmApplicationService bAlarmCommands(
            Alarms alarms,
            Users users,
            Settings settings,
            UnitOfWork tx,
            BEventPublisher events,
            AlarmWorkflowDecisionService rules,
            PartApplicationService parts,
            Clock backendBClock) {
        return new AlarmApplicationService(
                alarms, users, settings, tx, events, rules, parts, backendBClock);
    }

    @Bean
    public DutyApplicationService bDutyCommands(
            Duties duties,
            Users users,
            UnitOfWork tx,
            DutyScheduleDecisionService rules,
            Clock backendBClock) {
        return new DutyApplicationService(duties, users, tx, rules, backendBClock);
    }

    @Bean
    public LinkTokens bLinkTokens(RedisTelegramLinkTokenStore store) {
        return new LinkTokens() {
            public void save(long id, TelegramLinkTokenService.Issued token, Instant now) {
                store.save(id, token, now);
            }

            public Long consume(String token) {
                return store.consume(token);
            }
        };
    }

    @Bean
    public TelegramDeliveryService bDelivery(@Value("${stf.telegram.bot-token:}") String token) {
        TelegramDeliveryService.Sender sender =
                token.isBlank()
                        ? (chat, message) -> {
                            throw new IllegalStateException("Bot configuration required");
                        }
                        : new TelegramBotSender(token);
        return new TelegramDeliveryService(sender, duration -> Thread.sleep(duration.toMillis()));
    }

    @Bean
    public com.sfaasproject.stf.telegram.port.NotificationPort bNotificationPort(
            JdbcBDatabase db, TelegramDeliveryService delivery) {
        return new JdbcNotificationAdapter(db, delivery);
    }

    @Bean
    public TelegramApplicationService bTelegramCommands(
            Telegram store,
            LinkTokens tokens,
            Users users,
            TelegramLinkTokenService issuing,
            TelegramDeliveryService delivery,
            Clock backendBClock,
            com.sfaasproject.stf.auth.config.TelegramProperties properties) {
        String username = properties.configured() ? properties.normalizedBotUsername() : "";
        return new TelegramApplicationService(
                store, tokens, users, issuing, delivery, backendBClock, username);
    }

    @Bean
    @ConditionalOnProperty(
            name = "stf.backend-b.jobs-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public BJobs bJobs(
            Alarms alarms,
            Settings settings,
            Duties duties,
            InventoryRuntime inventory,
            UnitOfWork tx,
            BEventPublisher events,
            Clock backendBClock) {
        return new BJobs(alarms, settings, duties, inventory, tx, events, backendBClock);
    }

    @Bean
    public BEventHandlers bHandlers(
            EventClaims claims,
            UnitOfWork tx,
            InventoryRuntime inventory,
            AlarmApplicationService alarms,
            TelegramApplicationService telegram,
            Users users,
            Telegram connections,
            SimpMessagingTemplate socket) {
        return new BEventHandlers(
                claims, tx, inventory, alarms, telegram, users, connections, socket);
    }
}

package com.sfaasproject.stf.operations.config;

import com.sfaasproject.stf.alarm.repository.V7AlarmRepository;
import com.sfaasproject.stf.alarm.service.*;
import com.sfaasproject.stf.common.integration.JdbcV7Context;
import com.sfaasproject.stf.equipment.service.V7LineQueryService;
import com.sfaasproject.stf.inventory.service.*;
import com.sfaasproject.stf.operations.adapter.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.scheduler.V7ForecastJobs;
import com.sfaasproject.stf.operations.service.*;
import com.sfaasproject.stf.statistics.service.*;
import com.sfaasproject.stf.system.service.*;
import com.sfaasproject.stf.telegram.port.NotificationPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@V7Backend
public class V7BackendConfiguration {
    @Bean
    public V7Context v7Context(JdbcBDatabase db) {
        return new JdbcV7Context(db);
    }

    @Bean
    public V7AlarmRepository v7AlarmRepository(JdbcBDatabase db) {
        return new V7AlarmRepository(db);
    }

    @Bean
    public V7AlarmService v7Alarms(
            V7AlarmRepository repository,
            V7Context context,
            UnitOfWork tx,
            BEventPublisher events,
            Clock backendBClock,
            AlarmLifecycleService lifecycle) {
        return new V7AlarmService(repository, context, tx, events, backendBClock, lifecycle);
    }

    @Bean
    public V7InventoryService v7Inventory(
            JdbcBDatabase db,
            V7Context context,
            AssemblyForecastService forecast,
            RecoveryTimeEstimator repair,
            @Value("${stf.inventory.source:DEMO_PRODUCTION}") String source) {
        return new V7InventoryService(db, context, forecast, repair, source);
    }

    @Bean
    public V7StatisticsService v7Statistics(
            V7Context context,
            JdbcBDatabase db,
            V7AlarmService alarms,
            ClosedAlarmStatisticsService metrics,
            StopCauseStatisticsService causes,
            ActualLossCalculator actualLoss) {
        return new V7StatisticsService(context, db, alarms, metrics, causes, actualLoss);
    }

    @Bean
    public AssemblyStopRecorder assemblyStopRecorder(
            JdbcBDatabase db,
            V7Context context,
            V7AlarmRepository alarms,
            ActualLossCalculator loss,
            UnitOfWork tx) {
        return new AssemblyStopRecorder(
                new com.sfaasproject.stf.statistics.repository.JdbcAssemblyStopObservationQuery(
                        db, context, alarms),
                new com.sfaasproject.stf.statistics.repository.AssemblyStopRepository(db),
                loss,
                tx);
    }

    @Bean
    @ConditionalOnProperty(
            name = "stf.backend-b.jobs-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public AssemblyStopJobs assemblyStopJobs(AssemblyStopRecorder recorder) {
        return new AssemblyStopJobs(recorder);
    }

    @Bean
    public V7OverviewService v7Overview(V7Context context, V7AlarmService alarms) {
        return new V7OverviewService(context, alarms);
    }

    @Bean
    public V7SettingsService v7Settings(
            JdbcBDatabase db, V7Context context, UnitOfWork tx, AuditRecorder audit) {
        return new V7SettingsService(db, context, tx, audit);
    }

    @Bean
    public V7LineQueryService v7Lines(V7Context context) {
        return new V7LineQueryService(context);
    }

    @Bean
    public V7Realtime v7Realtime(
            SimpMessagingTemplate socket,
            V7Context context,
            Telegram connections,
            NotificationPort notifications,
            EventClaims claims) {
        return new V7Realtime(socket, context, connections, notifications, claims);
    }

    @Bean
    @ConditionalOnProperty(
            name = "stf.backend-b.jobs-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public V7ForecastJobs v7Jobs(
            V7InventoryService inventory,
            UnitOfWork tx,
            BEventPublisher events,
            com.sfaasproject.stf.equipment.service.V7EquipmentAlarmBridge equipment) {
        return new V7ForecastJobs(inventory, tx, events, equipment);
    }

    @Bean
    public com.sfaasproject.stf.measurement.repository.JdbcV7IngestionStore v7Store(
            JdbcBDatabase db,
            BEventPublisher events,
            V7AlarmService alarms,
            @Value("${stf.inventory.source:DEMO_PRODUCTION}") String source) {
        return new com.sfaasproject.stf.measurement.repository.JdbcV7IngestionStore(
                db, events, alarms, source);
    }

    @Bean
    public com.sfaasproject.stf.equipment.service.V7EquipmentAlarmBridge v7EquipmentAlarmBridge(
            JdbcBDatabase db,
            V7AlarmService alarms,
            com.sfaasproject.stf.measurement.repository.JdbcV7IngestionStore store,
            V7Context context,
            UnitOfWork tx) {
        return new com.sfaasproject.stf.equipment.service.V7EquipmentAlarmBridge(
                db, alarms, store, context, tx);
    }

    @Bean
    @ConditionalOnProperty(name = "stf.backend-b.ingestion-enabled", havingValue = "true")
    public BIngestionApplicationService v7Ingestion(
            com.sfaasproject.stf.measurement.repository.JdbcV7IngestionStore store,
            JdbcBDatabase db,
            V7AlarmService alarms,
            UnitOfWork tx,
            BEventPublisher events,
            Clock backendBClock,
            com.sfaasproject.stf.measurement.service.IngestionMessageGuard guard,
            com.sfaasproject.stf.measurement.service.IngestionValidationService validator,
            com.sfaasproject.stf.measurement.service.ProductionCounterService counters,
            com.sfaasproject.stf.alarm.service.AlarmDecisionService rules,
            com.sfaasproject.stf.equipment.service.EquipmentStatusDecisionService status,
            V7Context context,
            @Value("${stf.inventory.source:DEMO_PRODUCTION}") String source) {
        var processor =
                new com.sfaasproject.stf.measurement.service.IngestionProcessor(
                        store,
                        guard,
                        validator,
                        counters,
                        rules,
                        status,
                        () -> context.integer("ALARM_STREAK_COUNT", 3),
                        () -> context.integer("ALARM_STREAK_COUNT", 3),
                        () ->
                                java.time.Duration.ofSeconds(
                                        context.integer("COMM_TIMEOUT_SEC", 30)));
        return new BIngestionApplicationService(processor, tx, backendBClock);
    }
}

package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowDecisionService;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.duty.service.DutyScheduleDecisionService;
import com.sfaasproject.stf.operations.adapter.*;
import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.port.BReadPort.*;
import com.sfaasproject.stf.operations.scheduler.BJobs;
import com.sfaasproject.stf.operations.service.*;

import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

class JdbcBFlowTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    final OffsetDateTime now = OffsetDateTime.now(clock);
    JdbcTemplate jdbc;
    JdbcBDatabase db;
    JdbcBUsersSettings users;
    JdbcBAlarms alarms;
    JdbcBParts parts;
    JdbcBDuties duties;
    JdbcBInventory inventory;
    JdbcBReadPort reads;
    UnitOfWork tx;
    AlarmApplicationService commands;
    DutyApplicationService dutyCommands;
    ApplicationEventPublisher publisher;
    BEventPublisher events;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "UNACKNOWLEDGED,1", "ACKNOWLEDGED,1", "ACTION,1", "CLOSED,0"
    })
    void activeCountIncludesEveryNonClosedStateAndExcludesDeletedEquipment(String state, int expected) {
        jdbc.update("UPDATE alarm SET process_state=? WHERE alarm_id=1", state);
        assertEquals(expected, alarms.activeCount());
        assertEquals(expected, commands.activeCount());
        jdbc.update("UPDATE equipment SET deleted_at=? WHERE equipment_id=1",
                java.sql.Timestamp.valueOf(now.toLocalDateTime()));
        assertEquals(0, alarms.activeCount());
    }

    @BeforeEach
    void setup() throws Exception {
        var source =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:b"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        try (var connection = source.getConnection()) {
            String sql =
                    new String(
                            getClass()
                                    .getResourceAsStream("/db/migration/V1__init_schema.sql")
                                    .readAllBytes(),
                            StandardCharsets.UTF_8);
            sql =
                    sql.replaceAll("(?m)^ALTER TABLE `[^`]+` COMMENT = [^;]+;", "")
                            .replaceAll(
                                    "(?s)ALTER TABLE `alarm`\\s+ADD COLUMN `open_item_id`.*$", "");
            ScriptUtils.executeSqlScript(
                    connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            String v3 =
                    new String(
                            getClass()
                                    .getResourceAsStream(
                                            "/db/migration/V3__backend_b_contract_and_jobs.sql")
                                    .readAllBytes(),
                            StandardCharsets.UTF_8);
            var pattern = java.util.regex.Pattern.compile("ALTER TABLE ([a-z_]+) MODIFY ([^;]+);");
            var match = pattern.matcher(v3);
            var replacement = new StringBuffer();
            while (match.find()) {
                String statements =
                        Arrays.stream(match.group(2).split(", MODIFY "))
                                .map(
                                        column ->
                                                "ALTER TABLE "
                                                        + match.group(1)
                                                        + " ALTER COLUMN "
                                                        + column
                                                        + ";")
                                .collect(java.util.stream.Collectors.joining("\n"));
                match.appendReplacement(
                        replacement, java.util.regex.Matcher.quoteReplacement(statements));
            }
            match.appendTail(replacement);
            ScriptUtils.executeSqlScript(
                    connection,
                    new ByteArrayResource(replacement.toString().getBytes(StandardCharsets.UTF_8)));
        }
        jdbc = new JdbcTemplate(source);
        var manager = new DataSourceTransactionManager(source);
        var template = new TransactionTemplate(manager);
        tx =
                new UnitOfWork() {
                    public <T> T execute(Supplier<T> work) {
                        return template.execute(status -> work.get());
                    }
                };
        db = new JdbcBDatabase(jdbc, template, clock);
        users = new JdbcBUsersSettings(db);
        alarms = new JdbcBAlarms(db);
        parts = new JdbcBParts(db);
        duties = new JdbcBDuties(db, new JdbcBClaims(db));
        inventory = new JdbcBInventory(db);
        reads = new JdbcBReadPort(db, users, users, inventory, alarms);
        publisher = mock(ApplicationEventPublisher.class);
        events = new BEventPublisher(publisher);
        commands =
                new AlarmApplicationService(
                        alarms,
                        users,
                        users,
                        tx,
                        events,
                        new AlarmWorkflowDecisionService(),
                        new PartApplicationService(parts, users, tx),
                        clock);
        dutyCommands =
                new DutyApplicationService(
                        duties, users, tx, new DutyScheduleDecisionService(), clock, this::recordAudit);
        jdbc.update(
                "INSERT INTO account(account_id,login_id,name,role,approval_status) VALUES"
                    + " (1,'admin','관리자','SYSTEM_ADMIN','APPROVED'),(2,'maint','보전','MAINTENANCE','APPROVED'),(3,'manager','라인','LINE_MANAGER','APPROVED')");
        jdbc.update(
                "INSERT INTO line(line_id,name,line_type,sort_order) VALUES"
                        + " (1,'부품','PART',1),(2,'조립','ASSEMBLY',2)");
        jdbc.update(
                "INSERT INTO process(process_id,line_id,process_seq,name) VALUES"
                        + " (1,1,1,'검사'),(2,2,1,'조립검사')");
        jdbc.update(
                "INSERT INTO"
                        + " equipment(equipment_id,equip_code,name,equip_type,process_id,comm_type)"
                        + " VALUES"
                        + " (1,'OR-01-1','설비1','검사기',1,'MQTT'),(2,'AS-01-1','설비2','검사기',2,'MQTT')");
        jdbc.update(
                "INSERT INTO"
                    + " data_item(data_item_id,equipment_id,item_code,name,caution_limit,critical_limit,limit_direction)"
                    + " VALUES (1,1,'temperature','온도',10,20,'ABOVE')");
        jdbc.update(
                "INSERT INTO"
                    + " alarm(alarm_id,equipment_id,data_item_id,grade,message,trigger_value,limit_value,occurred_at)"
                    + " VALUES (1,1,1,'CRITICAL','온도 경보',25,20,?)",
                java.sql.Timestamp.valueOf(now.minusMinutes(20).toLocalDateTime()));
        jdbc.update(
                "INSERT INTO"
                    + " part(part_id,equipment_id,name,manage_basis,replace_limit,last_replaced_at)"
                    + " VALUES (1,1,'필터','ELAPSED_DAYS',20,?)",
                java.sql.Timestamp.valueOf(now.minusDays(16).toLocalDateTime()));
        jdbc.update(
                "INSERT INTO system_setting(setting_key,name,setting_group,setting_value) VALUES"
                    + " ('VERIFY_PASS_COUNT','목표','JUDGE','20'),('RENOTIFY_MIN','재알림','JUDGE','3'),('UNIT_PRICE','금액','CALC','100')");
        jdbc.update(
                "INSERT INTO alarm_log(alarm_id,event_type,content,created_at) VALUES"
                        + " (1,'OCCUR','온도 경보',?)",
                java.sql.Timestamp.valueOf(now.minusMinutes(20).toLocalDateTime()));
        login("SYSTEM_ADMIN");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void login(String role) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "1", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void acknowledgementActionInspectorPassClosesAndPersists() {
        var ack = commands.acknowledgeAlarm(1L, new AlarmAcknowledgementRequest(2L));
        assertEquals(2L, ack.data().assignee().userId());
        assertThrows(
                DomainException.class,
                () -> commands.acknowledgeAlarm(1L, new AlarmAcknowledgementRequest(2L)));
        commands.createAlarmAction(1L, new AlarmActionRequest("원인", "수리", null, null, null));
        commands.valueChanged(1, true, now);
        commands.inspectorPassed(1, 19, now);
        assertEquals(
                "ACTION_TAKEN",
                jdbc.queryForObject(
                        "SELECT process_state FROM alarm WHERE alarm_id=1", String.class));
        commands.inspectorPassed(1, 1, now);
        assertEquals(
                "CLOSED",
                jdbc.queryForObject(
                        "SELECT process_state FROM alarm WHERE alarm_id=1", String.class));
        assertEquals(
                "EFFECTIVE",
                jdbc.queryForObject(
                        "SELECT effect_result FROM alarm_action WHERE alarm_id=1", String.class));
        assertEquals(
                20,
                jdbc.queryForObject(
                        "SELECT verify_passed_count FROM alarm_action WHERE alarm_id=1",
                        Integer.class));
        verify(publisher, atLeastOnce()).publishEvent(any(BEvents.AlarmChanged.class));
    }

    @Test
    void invalidPartRollsBackActionAndEvents() {
        commands.acknowledgeAlarm(1L, new AlarmAcknowledgementRequest(2L));
        clearInvocations(publisher);
        assertThrows(
                DomainException.class,
                () ->
                        commands.createAlarmAction(
                                1L, new AlarmActionRequest("원인", "교체", 99L, now, "정기교체")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alarm_action", Integer.class));
        assertEquals(
                "ACKNOWLEDGED",
                jdbc.queryForObject(
                        "SELECT process_state FROM alarm WHERE alarm_id=1", String.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void falsePositiveClosesAndIncludesReason() {
        commands.acknowledgeAlarm(1L, new AlarmAcknowledgementRequest(2L));
        var result =
                commands.reportFalsePositive(
                        1L, new FalsePositiveRequest(FalsePositiveReason.OTHER, "시험 중"));
        assertEquals(AlarmResult.FALSE_POSITIVE, result.data().result());
        assertEquals(
                "시험 중",
                jdbc.queryForObject(
                        "SELECT false_positive_detail FROM alarm WHERE alarm_id=1", String.class));
    }

    @Test
    void dutyRejectsOverlapAndGapAndRendersMonth() {
        var first =
                dutyCommands.createAlarmDutySchedule(
                        new DutyScheduleWriteRequest(
                                now.toLocalDate(), now.toLocalDate().plusDays(2), 2L));
        assertThrows(
                DomainException.class,
                () ->
                        dutyCommands.createAlarmDutySchedule(
                                new DutyScheduleWriteRequest(
                                        now.toLocalDate().plusDays(2),
                                        now.toLocalDate().plusDays(4),
                                        2L)));
        assertThrows(
                DomainException.class,
                () ->
                        dutyCommands.createAlarmDutySchedule(
                                new DutyScheduleWriteRequest(
                                        now.toLocalDate().plusDays(4),
                                        now.toLocalDate().plusDays(5),
                                        2L)));
        assertEquals(30, dutyCommands.getAlarmDutyCalendar("2026-11").data().size());
        assertEquals(
                first.data().scheduleId(),
                dutyCommands.getCurrentAlarmDutySchedule().data().scheduleId());
        assertThrows(
                DomainException.class,
                () -> dutyCommands.deleteAlarmDutySchedule(first.data().scheduleId()));
    }

    @Test
    void reminderAndDutyTransitionAreIdempotent() {
        dutyCommands.createAlarmDutySchedule(
                new DutyScheduleWriteRequest(now.toLocalDate(), now.toLocalDate().plusDays(2), 2L));
        var jobs = new BJobs(alarms, users, duties, inventory, tx, events, clock);
        clearInvocations(publisher);
        jobs.remindUnacknowledged();
        jobs.remindUnacknowledged();
        verify(publisher, times(1)).publishEvent(any(BEvents.AlarmChanged.class));
        jobs.transitionDuty();
        jobs.transitionDuty();
        verify(publisher, times(1)).publishEvent(any(BEvents.DutyChanged.class));
    }

    @Test
    void inventoryUsesOnlyLineEndProductionAndConsumesEachPartLine() {
        tx.execute(
                () -> {
                    inventory.applyProduction("one", 1, 10, now);
                    return null;
                });
        assertEquals(10, inventory.recalculate(now).get(0).quantity());
        tx.execute(
                () -> {
                    inventory.applyProduction("two", 2, 3, now);
                    return null;
                });
        assertEquals(7, inventory.recalculate(now).get(0).quantity());
    }

    @Test
    void actualReadQueriesUseStoredRowsAndNotFixtures() {
        assertEquals(
                1,
                reads.getActiveAlarms(new GetActiveAlarmsQuery(null, null, null, null, 0, 20, null))
                        .data()
                        .items()
                        .size());
        assertEquals(
                "온도 경보", reads.getAlarmDetail(new GetAlarmDetailQuery(1L)).data().alarmMessage());
        assertEquals(
                2,
                reads.getEquipmentLayout(new GetEquipmentLayoutQuery(null))
                        .data()
                        .equipment()
                        .size());
        assertEquals(1, reads.getEquipmentParts(new GetEquipmentPartsQuery(1L)).data().size());
        assertEquals(
                PartUsageStatus.DUE_SOON,
                reads.getEquipmentParts(new GetEquipmentPartsQuery(1L)).data().get(0).status());
        assertTrue(
                reads.getPartReplacementHistories(
                                new GetPartReplacementHistoriesQuery(1L, 1L, null, null))
                        .data()
                        .items()
                        .isEmpty());
        assertEquals(
                1,
                reads.getDailyAlarmStatistics(
                                new GetDailyAlarmStatisticsQuery(now.minusDays(1), now, null))
                        .data()
                        .get(1)
                        .criticalCount());
        assertEquals(
                0,
                reads.getAlarmHistories(
                                new GetAlarmHistoriesQuery(
                                        now.minusDays(1), now, null, null, null, null, 0, 20, null))
                        .data()
                        .size());
    }

    @Test
    void everyJdbcReadProjectionMatchesItsResponseContract() throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            for (var c : BackendBControllerTest.cases()) {
                var found =
                        Arrays.stream(
                                        com.sfaasproject.stf.operations.port.BReadPort.class
                                                .getMethods())
                                .filter(m -> m.getName().equals(c.operation()))
                                .findFirst();
                if (found.isEmpty()) continue;
                var method = found.get();
                var queryType = method.getParameterTypes()[0];
                var input = new HashMap<String, Object>(c.params());
                for (var component : queryType.getRecordComponents()) {
                    if (component.getName().equals("page")) input.put("page", 0);
                    if (component.getName().equals("size")) input.put("size", 20);
                    if (c.path().contains("{" + component.getName() + "}"))
                        input.put(component.getName(), 1L);
                }
                Object query = json.convertValue(input, queryType);
                Object response =
                        tx.execute(
                                () -> {
                                    try {
                                        return method.invoke(reads, query);
                                    } catch (Exception e) {
                                        throw new RuntimeException(c.id() + " query failed", e);
                                    }
                                });
                validateRecord(factory.getValidator(), response, c.id());
            }
        }
    }

    void validateRecord(jakarta.validation.Validator validator, Object value, String path)
            throws Exception {
        if (value == null) return;
        if (value instanceof java.util.Collection<?> list) {
            for (var item : list) validateRecord(validator, item, path);
            return;
        }
        if (!value.getClass().isRecord()) return;
        assertTrue(
                validator.validate(value).isEmpty(),
                () -> path + " " + validator.validate(value).toString());
        for (var component : value.getClass().getRecordComponents())
            validateRecord(
                    validator,
                    component.getAccessor().invoke(value),
                    path + "." + component.getName());
    }

    @Test
    void fullMeasurementToAlarmToInspectorAutoClosureUsesRealSql() {
        jdbc.update("DELETE FROM alarm_log");
        jdbc.update("DELETE FROM alarm");
        jdbc.update(
                "INSERT INTO"
                    + " data_item(data_item_id,equipment_id,item_code,name,item_kind,item_role,reset_time)"
                    + " VALUES (2,1,'prod_count','생산수','CUMULATIVE','PRODUCTION','00:00:00')");
        var guard = mock(com.sfaasproject.stf.measurement.service.IngestionMessageGuard.class);
        when(guard.acquire(anyString()))
                .thenReturn(
                        com.sfaasproject.stf.measurement.service.IngestionMessageGuard.Acquisition
                                .ACQUIRED);
        var processor =
                new com.sfaasproject.stf.measurement.service.IngestionProcessor(
                        new JdbcIngestionStore(db, events, commands),
                        guard,
                        new com.sfaasproject.stf.measurement.service.IngestionValidationService(),
                        new com.sfaasproject.stf.measurement.service.ProductionCounterService(),
                        new com.sfaasproject.stf.alarm.service.AlarmDecisionService(),
                        new com.sfaasproject.stf.equipment.service
                                .EquipmentStatusDecisionService());
        var newTx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        newTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UnitOfWork afterCommitTx =
                new UnitOfWork() {
                    public <T> T execute(Supplier<T> work) {
                        return newTx.execute(status -> work.get());
                    }
                };
        var handlers =
                new BEventHandlers(
                        new JdbcBClaims(db),
                        afterCommitTx,
                        inventory,
                        commands,
                        mock(TelegramApplicationService.class),
                        users,
                        new JdbcBTelegram(db, users),
                        mock(org.springframework.messaging.simp.SimpMessagingTemplate.class));
        doAnswer(
                        call -> {
                            Object event = call.getArgument(0);
                            if (event instanceof BEvents.ProductionRecorded production)
                                handlers.production(production);
                            return null;
                        })
                .when(publisher)
                .publishEvent(any(Object.class));
        for (int i = 0; i < 3; i++)
            ingest(
                    processor,
                    "high" + i,
                    "temperature",
                    com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest.Value.Kind
                            .MEASUREMENT,
                    25,
                    now.minusSeconds(9 - i * 3));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM alarm", Integer.class));
        long alarm = jdbc.queryForObject("SELECT alarm_id FROM alarm", Long.class);
        commands.acknowledgeAlarm(alarm, new AlarmAcknowledgementRequest(2L));
        commands.createAlarmAction(alarm, new AlarmActionRequest("원인", "수리", null, null, null));
        for (int i = 0; i < 3; i++)
            ingest(
                    processor,
                    "normal" + i,
                    "temperature",
                    com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest.Value.Kind
                            .MEASUREMENT,
                    5,
                    now.plusSeconds(i * 3));
        assertEquals(
                "RECOVERED", jdbc.queryForObject("SELECT value_state FROM alarm", String.class));
        ingest(
                processor,
                "baseline",
                "prod_count",
                com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest.Value.Kind
                        .CUMULATIVE,
                100,
                now.plusSeconds(9));
        ingest(
                processor,
                "passed",
                "prod_count",
                com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest.Value.Kind
                        .CUMULATIVE,
                120,
                now.plusSeconds(12));
        assertEquals(
                "CLOSED", jdbc.queryForObject("SELECT process_state FROM alarm", String.class));
        assertEquals(
                20,
                jdbc.queryForObject("SELECT verify_passed_count FROM alarm_action", Integer.class));
        assertEquals(20, jdbc.queryForObject("SELECT qty FROM production_log", Integer.class));
    }

    void ingest(
            com.sfaasproject.stf.measurement.service.IngestionProcessor processor,
            String id,
            String code,
            com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest.Value.Kind kind,
            int value,
            OffsetDateTime at) {
        var req =
                new com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest(
                        id,
                        "OR-01-1",
                        at,
                        List.of(
                                new com.sfaasproject.stf.measurement.dto.MeasurementIngestionRequest
                                        .Value(code, kind, java.math.BigDecimal.valueOf(value))));
        tx.execute(() -> processor.process(req, at));
    }

    @Test
    void maximumContractActionLengthsAndPartLinkArePersistedAtomically() {
        commands.acknowledgeAlarm(1L, new AlarmAcknowledgementRequest(2L));
        var result =
                commands.createAlarmAction(
                        1L,
                        new AlarmActionRequest(
                                "c".repeat(1000), "x".repeat(4000), 1L, now, "자유 입력 교체 사유"));
        assertNotNull(result.data().partReplacementId());
        assertNotNull(jdbc.queryForObject("SELECT action_id FROM part_replacement", Long.class));
        assertEquals(
                4000,
                jdbc.queryForObject(
                        "SELECT LENGTH(action_content) FROM alarm_action", Integer.class));
        assertEquals(
                now.toLocalDateTime(),
                jdbc.queryForObject(
                                "SELECT last_replaced_at FROM part WHERE part_id=1",
                                java.sql.Timestamp.class)
                        .toLocalDateTime());
    }

    @Test
    void failedPostCommitEventIsRetainedAndRetried() {
        var target = mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("network"))
                .doNothing()
                .when(target)
                .publishEvent(any(Object.class));
        var outbox =
                new JdbcBEventOutbox(
                        jdbc,
                        new DataSourceTransactionManager(jdbc.getDataSource()),
                        target,
                        clock);
        var publisher = new BEventPublisher(target, outbox);
        tx.execute(
                () -> {
                    publisher.publish(new BEvents.DutyChanged("outbox-1", 1, 2, now));
                    return null;
                });
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM backend_event_outbox WHERE published_at IS NULL",
                        Integer.class));
        outbox.retryPending();
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM backend_event_outbox WHERE published_at IS NULL",
                        Integer.class));
        verify(target, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void rolledBackMutationLeavesNoOutboxEvent() {
        var outbox =
                new JdbcBEventOutbox(
                        jdbc,
                        new DataSourceTransactionManager(jdbc.getDataSource()),
                        publisher,
                        clock);
        var sender = new BEventPublisher(publisher, outbox);
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.execute(
                                () -> {
                                    sender.publish(new BEvents.DutyChanged("outbox-2", 1, 2, now));
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM backend_event_outbox", Integer.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void telegramConnectionAndDeliverySummaryAreBackedByDatabase() {
        var telegram = new JdbcBTelegram(db, users);
        telegram.link(3, "12345", now);
        assertEquals("12345", telegram.connection(3).chatId());
        telegram.record(new Delivery(1L, 3, "OCCUR", "경보", true, null, 0, now));
        telegram.record(new Delivery(1L, 3, "OCCUR", "경보", false, "발송 실패", 1, now));
        jdbc.update(
                "UPDATE account SET deleted_at=? WHERE account_id=3",
                java.sql.Timestamp.valueOf(now.toLocalDateTime()));
        var result = telegram.deliveries(now.toLocalDate(), null, 0, 20);
        assertEquals(2, result.data().summary().totalCount());
        assertEquals(1, result.data().summary().successCount());
        assertEquals(Position.LINE_MANAGER, result.data().items().get(0).recipient().position());
        telegram.unlink(3);
        assertNull(telegram.connection(3));
    }

    @Test
    void notificationPortKeepsRejectedRecipientSnapshotWithoutInventedAccount() {
        var sender =
                new com.sfaasproject.stf.telegram.service.TelegramDeliveryService(
                        (chat, msg) -> {}, duration -> {});
        var adapter = new JdbcNotificationAdapter(db, sender);
        var result =
                adapter.deliver(
                        new com.sfaasproject.stf.telegram.port.NotificationPort.Recipient(
                                null, "거절 신청자", "123"),
                        null,
                        "SIGNUP_RESULT",
                        "가입 신청 결과입니다.");
        assertTrue(result.successful());
        var log =
                new JdbcBTelegram(db, users)
                        .deliveries(now.toLocalDate(), null, 0, 20)
                        .data()
                        .items()
                        .get(0);
        assertNull(log.recipient());
        assertEquals("거절 신청자", log.recipientName());
    }

    @Test
    void realLossAndEquipmentRankingUseStoredStopCost() {
        jdbc.update(
                "INSERT INTO"
                    + " assembly_stop(started_at,ended_at,cause_equipment_id,hourly_output,unit_price,loss_amount)"
                    + " VALUES (?,?,1,60,100,1000)",
                java.sql.Timestamp.valueOf(now.minusMinutes(10).toLocalDateTime()),
                java.sql.Timestamp.valueOf(now.toLocalDateTime()));
        var loss =
                reads.getLossStatistics(new GetLossStatisticsQuery(now.minusHours(1), now, null));
        assertEquals(0, loss.data().todayLossAmount().compareTo(new java.math.BigDecimal("1000")));
        var ranked =
                reads.getEquipmentRankings(
                        new GetEquipmentRankingsQuery(
                                now.minusHours(1),
                                now,
                                null,
                                com.sfaasproject.stf.operations.dto.BContract
                                        .GetEquipmentRankingsSortBy.lossAmount));
        assertEquals(1L, ranked.data().get(0).equipmentId());
        assertEquals(
                0, ranked.data().get(0).lossAmount().compareTo(new java.math.BigDecimal("1000")));
    }

    @Test
    void requestedSortChangesOrderAndUnknownSortIsRejected() {
        jdbc.update(
                "INSERT INTO data_item(data_item_id,equipment_id,item_code,name) VALUES"
                        + " (10,1,'second','추가항목')");
        jdbc.update(
                "INSERT INTO"
                    + " alarm(alarm_id,equipment_id,data_item_id,grade,message,trigger_value,limit_value,occurred_at)"
                    + " VALUES (2,1,10,'CAUTION','추가알람',15,10,?)",
                java.sql.Timestamp.valueOf(now.minusMinutes(1).toLocalDateTime()));
        assertEquals(
                1L,
                reads.getActiveAlarms(new GetActiveAlarmsQuery(null, null, null, null, 0, 20, null))
                        .data()
                        .items()
                        .get(0)
                        .alarmId());
        assertEquals(
                2L,
                reads.getActiveAlarms(
                                new GetActiveAlarmsQuery(
                                        null, null, null, null, 0, 20, "occurredAt,desc"))
                        .data()
                        .items()
                        .get(0)
                        .alarmId());
        assertThrows(
                DomainException.class,
                () ->
                        reads.getActiveAlarms(
                                new GetActiveAlarmsQuery(
                                        null, null, null, null, 0, 20, "unknown,asc")));
    }

    @Test
    void availabilityIncludesEquipmentAndWeightedLineTotals() {
        jdbc.update(
                "INSERT INTO equipment_status_history(equipment_id,status,started_at,ended_at)"
                    + " VALUES (1,'RUNNING',?,?)",
                java.sql.Timestamp.valueOf(now.minusHours(1).toLocalDateTime()),
                java.sql.Timestamp.valueOf(now.minusMinutes(30).toLocalDateTime()));
        var metrics =
                reads.getAvailabilityStatistics(
                                new GetAvailabilityStatisticsQuery(now.minusHours(1), now, 1L))
                        .data();
        assertEquals(2, metrics.size());
        var line =
                metrics.stream()
                        .filter(m -> m.targetType().name().equals("LINE"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, line.availabilityRate().compareTo(new java.math.BigDecimal("50")));
        assertEquals(0, line.plannedMinutes().compareTo(new java.math.BigDecimal("60")));
    }
    void recordAudit(long actor, com.sfaasproject.stf.system.entity.AuditTargetType type,
            String target, String field, String before, String after) {
        jdbc.update("INSERT INTO change_log(changed_by,target_type,target_name,field_name,old_value,new_value,changed_at)"
                + " VALUES (?,?,?,?,?,?,?)",actor,type.name(),target,field,before,after,LocalDateTime.now(clock));
    }

}

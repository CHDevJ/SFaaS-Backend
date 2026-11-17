package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sfaasproject.stf.alarm.service.V7AlarmService;
import com.sfaasproject.stf.auth.service.*;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.measurement.service.IngestionMessageGuard;
import com.sfaasproject.stf.operations.dto.V7Contract.AlarmActionRequest;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.BPorts;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import tools.jackson.databind.json.JsonMapper;

import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {
            "stf.backend-b.enabled=true",
            "stf.backend-b.jobs-enabled=false",
            "stf.backend-b.ingestion-enabled=true",
            "stf.telegram.webhook-secret=v7-unit-test-callback",
            "INTERNAL_API_KEY=v7-unit-test-internal-key-0123456789",
            "spring.datasource.url=jdbc:h2:mem:v7-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
        })
@AutoConfigureMockMvc
@Import(V7BackendIntegrationTest.TestClock.class)
class V7BackendIntegrationTest {
    static class MutableClock extends Clock {
        volatile Instant current = Instant.parse("2026-11-17T01:00:00Z");

        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        public Instant instant() {
            return current;
        }

        void set(Instant value) {
            current = value;
        }
    }

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        Clock v7Clock() {
            return new MutableClock();
        }
    }

    @Autowired com.sfaasproject.stf.equipment.service.V7EquipmentAlarmBridge equipmentBridge;
    @Autowired com.sfaasproject.stf.statistics.service.AssemblyStopRecorder stopRecorder;
    @Autowired BEventPublisher events;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;
    @Autowired V7AlarmService alarms;
    @Autowired BPorts.UnitOfWork tx;
    @MockitoBean AccessRevocationStore revocations;
    @MockitoBean TelegramDeliveryService delivery;
    @MockitoBean BPorts.LinkTokens tokens;
    @MockitoBean IngestionMessageGuard guard;
    final JsonMapper json = JsonMapper.builder().build();
    final OffsetDateTime now = OffsetDateTime.parse("2026-11-17T10:00:00+09:00");
    String admin, user;

    @BeforeEach
    void data() {
        ((MutableClock) clock).set(now.toInstant());
        for (String table :
                List.of(
                        "system_setting",
                        "assembly_stop",
                        "backend_event_outbox",
                        "backend_event_claim",
                        "notification_log",
                        "alarm_log",
                        "backend_alarm_impact",
                        "alarm_action",
                        "alarm",
                        "measurement",
                        "measurement_latest",
                        "production_log",
                        "stock_history",
                        "equipment_status_history",
                        "equipment_state",
                        "part_replacement",
                        "part",
                        "data_item",
                        "equipment",
                        "process",
                        "line",
                        "change_log",
                        "auth_token",
                        "duty_member",
                        "duty",
                        "account")) jdbc.update("DELETE FROM " + table);
        jdbc.update(
                "INSERT INTO"
                    + " account(account_id,login_id,name,role,approval_status,telegram_chat_id)"
                    + " VALUES"
                    + " (1,'adminv71','관리자','ADMIN','APPROVED','101'),(2,'userv701','사용자','USER','APPROVED','102'),(3,'pending7','승인대기',NULL,'PENDING','103')");
        var codes = List.of("OR-06-1", "IR-06-1", "CG-06-1", "SH-05-1", "AS-04-1");
        for (int n = 1; n <= 5; n++) {
            jdbc.update(
                    "INSERT INTO line(line_id,name,line_type,sort_order) VALUES (?,?,?,?)",
                    n,
                    "라인" + n,
                    n == 5 ? "ASSEMBLY" : "PART",
                    n);
            jdbc.update(
                    "INSERT INTO process(process_id,line_id,process_seq,name) VALUES (?,?,1,?)",
                    n,
                    n,
                    "검사" + n);
            jdbc.update(
                    "INSERT INTO"
                        + " equipment(equipment_id,equip_code,name,equip_type,process_id,comm_type,registered_at)"
                        + " VALUES (?,?,?,'검사기',?,'MQTT',?)",
                    n,
                    codes.get(n - 1),
                    "설비" + n,
                    n,
                    now.minusDays(10).toLocalDateTime());
            jdbc.update(
                    "INSERT INTO equipment_state(equipment_id,status,status_since,last_received_at)"
                            + " VALUES (?,?,?,?)",
                    n,
                    n == 1 ? "FAULT" : "RUNNING",
                    now.toLocalDateTime(),
                    now.toLocalDateTime());
            jdbc.update(
                    "INSERT INTO"
                        + " data_item(data_item_id,equipment_id,item_code,name,item_kind,item_role,decimal_places)"
                        + " VALUES (?,?,?,?,'CUMULATIVE','PRODUCTION',0)",
                    n * 10 + 1,
                    n,
                    "production_count",
                    "누적 생산");
            jdbc.update(
                    "INSERT INTO"
                        + " measurement_latest(data_item_id,measured_value,measured_at,received_at)"
                        + " VALUES (?,1000,?,?)",
                    n * 10 + 1,
                    now.minusSeconds(1).toLocalDateTime(),
                    now.minusSeconds(1).toLocalDateTime());
            jdbc.update(
                    "INSERT INTO production_log(equipment_id,minute_at,qty) VALUES (?,?,?)",
                    n,
                    now.minusMinutes(1).toLocalDateTime(),
                    n == 5 ? 60 : 10);
            jdbc.update(
                    "INSERT INTO equipment_status_history(equipment_id,status,started_at,ended_at)"
                            + " VALUES (?,'RUNNING',?,?)",
                    n,
                    now.minusHours(1).toLocalDateTime(),
                    now.toLocalDateTime());
            if (n < 5)
                jdbc.update(
                        "INSERT INTO stock_history(line_id,recorded_at,qty) VALUES (?,?,300)",
                        n,
                        now.minusMinutes(1).toLocalDateTime());
        }
        jdbc.update(
                "INSERT INTO"
                    + " data_item(data_item_id,equipment_id,item_code,name,item_kind,caution_limit,critical_limit,limit_direction,decimal_places)"
                    + " VALUES"
                    + " (101,1,'coolant_temp','온도','MEASUREMENT',10,20,'ABOVE',2),(102,1,'coolant_flow','유량','MEASUREMENT',8,5,'BELOW',3)");
        jdbc.update(
                "INSERT INTO"
                        + " measurement_latest(data_item_id,measured_value,measured_at,received_at)"
                        + " VALUES (101,30,?,?)",
                now.toLocalDateTime(),
                now.toLocalDateTime());
        jdbc.update(
                "INSERT INTO measurement(data_item_id,measured_at,measured_value) VALUES"
                        + " (101,?,30)",
                now.toLocalDateTime());
        jdbc.update(
                "INSERT INTO"
                    + " alarm(alarm_id,equipment_id,data_item_id,grade,message,trigger_value,limit_value,process_state,occurred_at)"
                    + " VALUES (100,1,101,'CRITICAL','온도 경보',30,20,'ACTIVE',?)",
                now.toLocalDateTime());
        for (int n = 0; n < 5; n++) {
            var at = now.minusDays(2).minusHours(n * 2);
            jdbc.update(
                    "INSERT INTO"
                        + " alarm(alarm_id,equipment_id,data_item_id,grade,message,trigger_value,limit_value,process_state,occurred_at,closed_at)"
                        + " VALUES (?,1,101,'CRITICAL','과거 경보',30,20,'CLOSED',?,?)",
                    200 + n,
                    at.toLocalDateTime(),
                    at.plusMinutes(70).toLocalDateTime());
            jdbc.update(
                    "INSERT INTO"
                        + " alarm_action(alarm_id,equipment_text,cause,action_content,acted_by,acted_at)"
                        + " VALUES (?,'설비1','원인','정비',1,?)",
                    200 + n,
                    at.plusMinutes(70).toLocalDateTime());
        }
        for (var e :
                Map.of(
                                "COMM_TIMEOUT_SEC",
                                "30",
                                "ALARM_STREAK_COUNT",
                                "3",
                                "STOCK_LEVEL_MIN",
                                "30",
                                "STOCK_DANGER_MIN",
                                "10",
                                "RATE_WINDOW_MIN",
                                "10",
                                "HOURLY_OUTPUT",
                                "360",
                                "UNIT_PRICE",
                                "50000")
                        .entrySet())
            jdbc.update(
                    "INSERT INTO system_setting(setting_key,name,setting_group,setting_value)"
                            + " VALUES (?,?,?,?)",
                    e.getKey(),
                    e.getKey(),
                    e.getKey().equals("UNIT_PRICE")
                                    || e.getKey().equals("HOURLY_OUTPUT")
                                    || e.getKey().equals("RATE_WINDOW_MIN")
                            ? "CALC"
                            : "JUDGE",
                    e.getValue());
        admin = "Bearer " + jwt.issue(1, Permission.ADMIN, clock.instant()).value();
        user = "Bearer " + jwt.issue(2, Permission.USER, clock.instant()).value();
        when(delivery.sendWithOneRetry(anyString(), anyString()))
                .thenReturn(new TelegramDeliveryService.DeliveryResult(true, 0, null));
        when(tokens.consume("link-code")).thenReturn(2L);
        Set<String> messages = ConcurrentHashMap.newKeySet();
        when(guard.acquire(anyString()))
                .thenAnswer(
                        a ->
                                messages.add(a.getArgument(0))
                                        ? IngestionMessageGuard.Acquisition.ACQUIRED
                                        : IngestionMessageGuard.Acquisition.COMPLETED);
    }

    MvcResult getOk(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void goldenForecastAndAllDisplayMetadataMatchLatestDecision() throws Exception {
        mvc.perform(get("/api/v1/overview/assembly-stop-forecast").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].lineId").value(1))
                .andExpect(jsonPath("$.data.lines[0].remainingMinutes").value(50))
                .andExpect(jsonPath("$.data.lines[0].repairHistoryCount").value(5))
                .andExpect(jsonPath("$.data.lines[0].averageRepairMinutes").value(70))
                .andExpect(jsonPath("$.data.lines[0].shortageMinutes").value(20))
                .andExpect(jsonPath("$.data.lines[0].expectedLossAmount").value(6000000))
                .andExpect(jsonPath("$.data.lines[0].unitPriceConfigured").value(true));
        mvc.perform(get("/api/v1/alarms").header("Authorization", user))
                .andExpect(jsonPath("$.data.items[0].processStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].cautionThreshold").value(10))
                .andExpect(jsonPath("$.data.items[0].thresholdDirection").value("ABOVE"))
                .andExpect(jsonPath("$.data.items[0].decimalPlaces").value(2));
        mvc.perform(get("/api/v1/lines").header("Authorization", user))
                .andExpect(jsonPath("$.data[0].lineType").value("PART"))
                .andExpect(jsonPath("$.data[4].lineType").value("ASSEMBLY"));
    }

    @Test
    void communicationTimeoutInvalidatesForecastAndRecoveryResumesIt() throws Exception {
        jdbc.update(
                "INSERT INTO"
                        + " data_item(data_item_id,equipment_id,item_code,name,item_kind,item_role)"
                        + " VALUES (52,5,'run_signal','가동 신호','STATE','RUN_SIGNAL')");
        jdbc.update(
                "INSERT INTO"
                        + " measurement_latest(data_item_id,measured_value,measured_at,received_at)"
                        + " VALUES (52,1,?,?)",
                now.toLocalDateTime(),
                now.toLocalDateTime());
        ((MutableClock) clock).set(now.plusSeconds(30).toInstant());
        equipmentBridge.refreshCommunication();
        assertEquals(
                "RUNNING",
                jdbc.queryForObject(
                        "SELECT status FROM equipment_state WHERE equipment_id=5", String.class));
        ((MutableClock) clock).set(now.plusSeconds(31).toInstant());
        equipmentBridge.refreshCommunication();
        assertEquals(
                "COMMUNICATION_LOST",
                jdbc.queryForObject(
                        "SELECT status FROM equipment_state WHERE equipment_id=5", String.class));
        mvc.perform(get("/api/v1/overview/assembly-stop-forecast").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].forecastStatus").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.data.lines[0].expectedLossAmount").isEmpty());
        int transitions =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM equipment_status_history WHERE"
                                + " status='COMMUNICATION_LOST'",
                        Integer.class);
        equipmentBridge.refreshCommunication();
        assertEquals(
                transitions,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM equipment_status_history WHERE"
                                + " status='COMMUNICATION_LOST'",
                        Integer.class));
        ingest(
                "as-reconnected",
                "AS-04-1",
                "production_count",
                "CUMULATIVE",
                1001,
                now.plusSeconds(31));
        assertEquals(
                "RUNNING",
                jdbc.queryForObject(
                        "SELECT status FROM equipment_state WHERE equipment_id=5", String.class));
    }

    @Test
    void actualLossAggregatesBeforeRoundingAndKeepsHistoricalSnapshots() throws Exception {
        jdbc.update(
                "INSERT INTO"
                    + " assembly_stop(started_at,ended_at,cause_equipment_id,hourly_output,unit_price)"
                    + " VALUES (?,?,1,1080,1),(?,?,1,1080,1)",
                now.minusSeconds(2).toLocalDateTime(),
                now.minusSeconds(1).toLocalDateTime(),
                now.minusSeconds(1).toLocalDateTime(),
                now.toLocalDateTime());
        mvc.perform(
                        get("/api/v1/statistics/losses")
                                .param("from", now.minusMinutes(1).toString())
                                .param("to", now.plusSeconds(1).toString())
                                .header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayLossAmount").value(1))
                .andExpect(jsonPath("$.data.daily[0].lossAmount").value(1));
        mvc.perform(
                        get("/api/v1/statistics/equipment-rankings")
                                .param("from", now.minusMinutes(1).toString())
                                .param("to", now.plusSeconds(1).toString())
                                .param("sortBy", "lossAmount")
                                .header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].equipmentId").value(1))
                .andExpect(jsonPath("$.data[0].lossAmount").value(1));
        jdbc.update(
                "UPDATE system_setting SET setting_value='99999' WHERE setting_key IN"
                        + " ('HOURLY_OUTPUT','UNIT_PRICE')");
        mvc.perform(
                        get("/api/v1/statistics/losses")
                                .param("from", now.minusMinutes(1).toString())
                                .param("to", now.plusSeconds(1).toString())
                                .header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayLossAmount").value(1));
    }

    @Test
    void assemblyFaultStartsOneActualStopAndRecoveryUsesOriginalCostSnapshot() throws Exception {
        jdbc.update(
                "UPDATE equipment_state SET status='FAULT',status_since=? WHERE equipment_id=5",
                now.toLocalDateTime());
        stopRecorder.refresh();
        stopRecorder.refresh();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
        assertEquals(
                5,
                jdbc.queryForObject("SELECT cause_equipment_id FROM assembly_stop", Integer.class));
        assertNull(jdbc.queryForObject("SELECT ended_at FROM assembly_stop", Object.class));
        jdbc.update("UPDATE system_setting SET setting_value='1' WHERE setting_key='UNIT_PRICE'");
        ((MutableClock) clock).set(now.plusMinutes(20).toInstant());
        mvc.perform(
                        get("/api/v1/statistics/losses")
                                .param("from", now.toString())
                                .param("to", now.plusMinutes(30).toString())
                                .header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily[0].lossAmount").value(6000000));
        jdbc.update(
                "UPDATE equipment_state SET status='RUNNING',status_since=? WHERE equipment_id=5",
                now.plusMinutes(20).toLocalDateTime());
        stopRecorder.refresh();
        stopRecorder.refresh();
        assertEquals(
                6000000, jdbc.queryForObject("SELECT loss_amount FROM assembly_stop", Long.class));
        assertEquals(
                50000, jdbc.queryForObject("SELECT unit_price FROM assembly_stop", Long.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
    }

    @Test
    void zeroStockStartsStopWithSourceAlarmAndClosesOnlyAfterEveryCauseClears() {
        jdbc.update("UPDATE stock_history SET qty=0 WHERE line_id=1");
        stopRecorder.refresh();
        assertEquals(
                1,
                jdbc.queryForObject("SELECT shortage_line_id FROM assembly_stop", Integer.class));
        assertEquals(
                100,
                jdbc.queryForObject("SELECT cause_alarm_id FROM assembly_stop", Integer.class));
        jdbc.update(
                "UPDATE equipment_state SET status='FAULT',status_since=? WHERE equipment_id=5",
                now.toLocalDateTime());
        jdbc.update("UPDATE stock_history SET qty=1 WHERE line_id=1");
        ((MutableClock) clock).set(now.plusMinutes(1).toInstant());
        stopRecorder.refresh();
        assertNull(jdbc.queryForObject("SELECT ended_at FROM assembly_stop", Object.class));
        jdbc.update("UPDATE equipment_state SET status='RUNNING' WHERE equipment_id=5");
        stopRecorder.refresh();
        assertEquals(
                300000, jdbc.queryForObject("SELECT loss_amount FROM assembly_stop", Long.class));
    }

    @Test
    void unattributedShortageIsIncludedInItsLineStatisticsWithoutInventingEquipment()
            throws Exception {
        jdbc.update("UPDATE stock_history SET qty=0 WHERE line_id=2");
        stopRecorder.refresh();
        ((MutableClock) clock).set(now.plusMinutes(1).toInstant());
        mvc.perform(
                        get("/api/v1/statistics/losses")
                                .param("from", now.toString())
                                .param("to", now.plusMinutes(2).toString())
                                .param("lineId", "2")
                                .header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily[0].lossAmount").value(300000))
                .andExpect(jsonPath("$.data.daily[0].causeEquipmentId").isEmpty());
    }

    @Test
    void communicationLossAloneDoesNotInventAnActualStopOrAnUnverifiedRecovery() {
        jdbc.update("UPDATE equipment_state SET status='COMMUNICATION_LOST' WHERE equipment_id=5");
        stopRecorder.refresh();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
        jdbc.update("UPDATE stock_history SET qty=0 WHERE line_id=1");
        stopRecorder.refresh();
        jdbc.update("UPDATE stock_history SET qty=300 WHERE line_id=1");
        ((MutableClock) clock).set(now.plusMinutes(1).toInstant());
        stopRecorder.refresh();
        assertNull(jdbc.queryForObject("SELECT ended_at FROM assembly_stop", Object.class));
        jdbc.update("UPDATE equipment_state SET status='RUNNING' WHERE equipment_id=5");
        stopRecorder.refresh();
        assertNotNull(jdbc.queryForObject("SELECT ended_at FROM assembly_stop", Object.class));
    }

    @Test
    void concurrentStopReconciliationCreatesOneRowAndFailedOuterTransactionRollsBack()
            throws Exception {
        jdbc.update("UPDATE stock_history SET qty=0 WHERE line_id=1");
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.execute(
                                () -> {
                                    stopRecorder.refresh();
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> stopRecorder.refresh());
            var two = executor.submit(() -> stopRecorder.refresh());
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
    }

    @Test
    void reconciliationDoesNotBackdateUnknownCostsOrPredictiveDepletion() {
        stopRecorder.refresh();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
        jdbc.update(
                "UPDATE stock_history SET qty=0,recorded_at=? WHERE line_id=1",
                now.minusDays(1).toLocalDateTime());
        stopRecorder.refresh();
        assertEquals(
                java.sql.Timestamp.valueOf(now.toLocalDateTime()),
                jdbc.queryForObject(
                        "SELECT started_at FROM assembly_stop", java.sql.Timestamp.class));
    }

    @Test
    void committedEquipmentEventCreatesStopAndRollbackOrReplayDoesNotDuplicateIt() {
        var event =
                new V7Events.EquipmentCommitted(
                        "assembly-fault",
                        1,
                        5,
                        com.sfaasproject.stf.operations.dto.V7Contract.EquipmentStatus.FAULT,
                        now,
                        now);
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.execute(
                                () -> {
                                    jdbc.update(
                                            "UPDATE equipment_state SET"
                                                    + " status='FAULT',status_since=? WHERE"
                                                    + " equipment_id=5",
                                            now.toLocalDateTime());
                                    events.publish(event);
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
        tx.execute(
                () -> {
                    jdbc.update(
                            "UPDATE equipment_state SET status='FAULT',status_since=? WHERE"
                                    + " equipment_id=5",
                            now.toLocalDateTime());
                    events.publish(event);
                    assertEquals(
                            0,
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM assembly_stop", Integer.class));
                    return null;
                });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
        tx.execute(
                () -> {
                    events.publish(event);
                    return null;
                });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
    }

    @Test
    void actualLossClipsAcrossMidnightAndCountsOverlappingStopsOnce() throws Exception {
        var start = now.withHour(23).withMinute(59).withSecond(59);
        var midnight = start.plusSeconds(1);
        ((MutableClock) clock).set(midnight.plusSeconds(5).toInstant());
        String fresh = "Bearer " + jwt.issue(1, Permission.ADMIN, clock.instant()).value();
        jdbc.update(
                "INSERT INTO"
                    + " assembly_stop(started_at,ended_at,cause_equipment_id,hourly_output,unit_price)"
                    + " VALUES (?,?,1,3600,1),(?,?,2,3600,1)",
                start.toLocalDateTime(),
                start.plusSeconds(3).toLocalDateTime(),
                midnight.toLocalDateTime(),
                midnight.plusSeconds(3).toLocalDateTime());
        mvc.perform(
                        get("/api/v1/statistics/losses")
                                .param("from", start.toString())
                                .param("to", midnight.plusSeconds(5).toString())
                                .header("Authorization", fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayLossAmount").value(3))
                .andExpect(jsonPath("$.data.weeklyLossAmount").value(4))
                .andExpect(jsonPath("$.data.daily[0].lossAmount").value(1))
                .andExpect(jsonPath("$.data.daily[1].lossAmount").value(3));
        mvc.perform(
                        get("/api/v1/statistics/equipment-rankings")
                                .param("from", start.toString())
                                .param("to", midnight.plusSeconds(5).toString())
                                .param("sortBy", "lossAmount")
                                .header("Authorization", fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].equipmentId").value(1))
                .andExpect(jsonPath("$.data[0].lossAmount").value(3))
                .andExpect(jsonPath("$.data[1].equipmentId").value(2))
                .andExpect(jsonPath("$.data[1].lossAmount").value(1));
        mvc.perform(
                        get("/api/v1/statistics/losses")
                                .param("from", start.toString())
                                .param("to", midnight.plusSeconds(5).toString())
                                .param("lineId", "2")
                                .header("Authorization", fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayLossAmount").value(1))
                .andExpect(jsonPath("$.data.daily[1].lossAmount").value(1));
        mvc.perform(
                        get("/api/v1/statistics/equipment-rankings")
                                .param("from", start.toString())
                                .param("to", midnight.plusSeconds(5).toString())
                                .param("sortBy", "lossAmount")
                                .param("lineId", "2")
                                .header("Authorization", fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].equipmentId").value(2))
                .andExpect(jsonPath("$.data[0].lossAmount").value(1));
    }

    @Test
    void missingAssemblyMetadataDoesNotInventAStoppedAssemblyLine() {
        jdbc.update("UPDATE line SET deleted_at=? WHERE line_id=5", now.toLocalDateTime());
        jdbc.update("UPDATE stock_history SET qty=0 WHERE line_id=1");
        stopRecorder.refresh();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
    }

    @Test
    void noHistoryDoesNotLoseDepletionAndZeroPriceIsDifferentFromMissingHistory() throws Exception {
        jdbc.update("UPDATE alarm SET grade='CAUTION' WHERE process_state='CLOSED'");
        mvc.perform(get("/api/v1/overview/assembly-stop-forecast").header("Authorization", user))
                .andExpect(jsonPath("$.data.lines[0].repairHistoryCount").value(0))
                .andExpect(jsonPath("$.data.lines[0].remainingMinutes").value(50))
                .andExpect(jsonPath("$.data.lines[0].expectedLossAmount").isEmpty())
                .andExpect(jsonPath("$.data.lines[0].forecastStatus").value("NO_RECOVERY_HISTORY"));
        jdbc.update("UPDATE alarm SET grade='CRITICAL' WHERE process_state='CLOSED'");
        jdbc.update("UPDATE system_setting SET setting_value='0' WHERE setting_key='UNIT_PRICE'");
        mvc.perform(get("/api/v1/overview/assembly-stop-forecast").header("Authorization", user))
                .andExpect(jsonPath("$.data.lines[0].expectedLossAmount").value(0))
                .andExpect(jsonPath("$.data.lines[0].unitPriceConfigured").value(false));
    }

    @Test
    void completionIsImmediateAtomicAndDuplicateIs409() throws Exception {
        var request =
                post("/api/v1/alarms/100/actions")
                        .header("Authorization", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"equipmentText\":\"자유 입력 설비\",\"actionContent\":\"조치"
                                        + " 완료\",\"cause\":\" \"}");
        mvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.processStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.cause").value("온도 경보"))
                .andExpect(jsonPath("$.data.handledBy.permission").value("USER"));
        assertEquals(
                "ABNORMAL",
                jdbc.queryForObject(
                        "SELECT value_state FROM alarm WHERE alarm_id=100", String.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM alarm_action WHERE alarm_id=100", Integer.class));
        mvc.perform(request).andExpect(status().isConflict());
        verifyNoInteractions(delivery);
    }

    @Test
    void rollbackDoesNotPublishCloseOrSaveAction() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.execute(
                                () -> {
                                    alarms.complete(
                                            100, new AlarmActionRequest("설비", null, "조치"), 2);
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT process_state FROM alarm WHERE alarm_id=100", String.class));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM backend_event_outbox", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM alarm_action WHERE alarm_id=100", Integer.class));
        verifyNoInteractions(delivery);
    }

    @Test
    void concurrentCompletionsYieldOneActionAndOneConflict() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var latch = new CountDownLatch(1);
            var jobs = new ArrayList<Future<Integer>>();
            for (int n = 0; n < 2; n++)
                jobs.add(
                        pool.submit(
                                () -> {
                                    latch.await();
                                    try {
                                        alarms.complete(
                                                100, new AlarmActionRequest("설비", null, "조치"), 2);
                                        return 201;
                                    } catch (com.sfaasproject.stf.common.DomainException e) {
                                        return e.status().value();
                                    }
                                }));
            latch.countDown();
            var results = new ArrayList<Integer>();
            for (var f : jobs) results.add(f.get(20, TimeUnit.SECONDS));
            Collections.sort(results);
            assertEquals(List.of(201, 409), results);
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM alarm_action WHERE alarm_id=100", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void settingsAreAdminOnlyAndChangesImmediatelyAffectPredictions() throws Exception {
        String body =
                "{\"communicationLossSeconds\":30,\"alarmConsecutiveCount\":3,\"inventoryWarningMinutes\":60,\"inventoryDangerMinutes\":50}";
        mvc.perform(
                        put("/api/v1/system/settings/judgement")
                                .header("Authorization", user)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/v1/system/settings/judgement")
                                .header("Authorization", admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/inventory/lines/1").header("Authorization", user))
                .andExpect(jsonPath("$.data.stage").value("DANGER"))
                .andExpect(jsonPath("$.data.stageThresholds.inventoryDangerMinutes").value(50))
                .andExpect(jsonPath("$.data.cycleTimeSeconds").doesNotExist())
                .andExpect(jsonPath("$.data.appropriateInventory").doesNotExist());
        mvc.perform(
                        put("/api/v1/system/settings/judgement")
                                .header("Authorization", admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        body.replace(
                                                "\"inventoryDangerMinutes\":50",
                                                "\"inventoryDangerMinutes\":60")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resetCounterKeepsLatestStockAndSuppressesUnverifiedSpeed() throws Exception {
        jdbc.update(
                "UPDATE measurement_latest SET"
                        + " counter_reset_at=?,counter_status='COUNTER_RESET_PENDING' WHERE"
                        + " data_item_id=11",
                now.minusMinutes(1).toLocalDateTime());
        mvc.perform(get("/api/v1/inventory/lines/1").header("Authorization", user))
                .andExpect(jsonPath("$.data.quantity").value(300))
                .andExpect(jsonPath("$.data.productionPerMinute").isEmpty())
                .andExpect(jsonPath("$.data.calculationStatus").value("COUNTER_RESET_PENDING"));
    }

    @Test
    void removedApisAreNotRegisteredInV7AndBadPeriodsAre400() throws Exception {
        for (String path :
                List.of(
                        "/api/v1/notifications",
                        "/api/v1/alarms/100/effect",
                        "/api/v1/alarms/100/recommended-actions",
                        "/api/v1/statistics/loss-avoidance",
                        "/api/v1/parts/usage",
                        "/api/v1/system/alarm-duty-schedules"))
            mvc.perform(get(path).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/v1/statistics/alarm-response")
                                .param("from", now.toString())
                                .param("to", now.toString())
                                .header("Authorization", user))
                .andExpect(status().isBadRequest());
    }

    @Test
    void callbackRequiresIndependentSecretIsIdempotentAndRejectsGroupChats() throws Exception {
        String body =
                "{\"update_id\":7,\"message\":{\"text\":\"/start"
                        + " link-code\",\"chat\":{\"id\":900}}}";
        mvc.perform(
                        post("/internal/v1/telegram/updates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/internal/v1/telegram/updates")
                                .header("X-Telegram-Bot-Api-Secret-Token", "v7-unit-test-callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(true));
        mvc.perform(
                        post("/internal/v1/telegram/updates")
                                .header("X-Telegram-Bot-Api-Secret-Token", "v7-unit-test-callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(false));
        verify(tokens, times(1)).consume("link-code");
    }

    @Test
    void passwordRequiredUserCannotUseBusinessApi() {
        jdbc.update("UPDATE account SET must_change_password=TRUE WHERE account_id=2");
        assertDoesNotThrow(
                () ->
                        mvc.perform(get("/api/v1/inventory").header("Authorization", user))
                                .andExpect(status().isForbidden())
                                .andExpect(
                                        jsonPath("$.error.code")
                                                .value("AUTH_PASSWORD_CHANGE_REQUIRED")));
    }

    @Test
    void v7DeletionAndPermissionChangePreserveUnusedDutyHistory() throws Exception {
        jdbc.update(
                "INSERT INTO duty(duty_id,start_date,end_date) VALUES"
                        + " (1,'2026-11-17','2026-11-20')");
        jdbc.update("INSERT INTO duty_member(duty_id,account_id) VALUES (1,2)");
        mvc.perform(
                        put("/api/v1/system/users/2")
                                .header("Authorization", admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"사용자\",\"permission\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM duty_member", Integer.class));
        mvc.perform(delete("/api/v1/system/users/2").header("Authorization", admin))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM duty_member", Integer.class));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT deleted_by FROM account WHERE account_id=2", Long.class));
        mvc.perform(get("/api/v1/inventory").header("Authorization", user))
                .andExpect(status().isUnauthorized());
    }

    MvcResult ingest(
            String id,
            String equipment,
            String item,
            String kind,
            int value,
            OffsetDateTime measured)
            throws Exception {
        ((MutableClock) clock)
                .set(
                        measured.toInstant().isAfter(clock.instant())
                                ? measured.toInstant()
                                : clock.instant());
        String body =
                json.writeValueAsString(
                        Map.of(
                                "messageId",
                                id,
                                "equipmentCode",
                                equipment,
                                "measuredAt",
                                measured.toString(),
                                "values",
                                List.of(
                                        Map.of(
                                                "dataItemCode",
                                                item,
                                                "valueType",
                                                kind,
                                                "value",
                                                value))));
        return mvc.perform(
                        post("/internal/v1/measurements")
                                .header(
                                        "X-Internal-Api-Key",
                                        "v7-unit-test-internal-key-0123456789")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(
                        result -> {
                            if (result.getResponse().getStatus() != 202)
                                throw new AssertionError(
                                        "Ingestion HTTP " + result.getResponse().getStatus(),
                                        result.getResolvedException());
                        })
                .andReturn();
    }

    @Test
    void ingestionCreatesFreshAlarmOnlyAfterThreeReadingsAndRecoveryDoesNotCloseIt()
            throws Exception {
        alarms.complete(100, new AlarmActionRequest("설비", null, "조치"), 2);
        for (int n = 1; n <= 2; n++) {
            ingest(
                    "temperature-" + n,
                    "OR-06-1",
                    "coolant_temp",
                    "MEASUREMENT",
                    30,
                    now.plusSeconds(n));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM alarm WHERE process_state='ACTIVE'",
                            Integer.class));
        }
        ingest("temperature-3", "OR-06-1", "coolant_temp", "MEASUREMENT", 30, now.plusSeconds(3));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM alarm WHERE process_state='ACTIVE'", Integer.class));
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notification_log WHERE notify_type='CREATED'",
                        Integer.class));
        long alarm =
                jdbc.queryForObject(
                        "SELECT alarm_id FROM alarm WHERE process_state='ACTIVE'", Long.class);
        assertNotEquals(100, alarm);
        for (int n = 4; n <= 6; n++)
            ingest(
                    "temperature-" + n,
                    "OR-06-1",
                    "coolant_temp",
                    "MEASUREMENT",
                    5,
                    now.plusSeconds(n));
        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT process_state FROM alarm WHERE alarm_id=?", String.class, alarm));
        assertEquals(
                "RECOVERED",
                jdbc.queryForObject(
                        "SELECT value_state FROM alarm WHERE alarm_id=?", String.class, alarm));
        assertEquals(
                3, jdbc.queryForObject("SELECT COUNT(*) FROM notification_log", Integer.class));
    }

    @Test
    void cumulativeProductionIsAppliedOnceOnlyFromTerminalMachinesAndResetStartsNewEpoch()
            throws Exception {
        ingest(
                "or-production",
                "OR-06-1",
                "production_count",
                "CUMULATIVE",
                1005,
                now.plusSeconds(1));
        ingest(
                "or-production",
                "OR-06-1",
                "production_count",
                "CUMULATIVE",
                1005,
                now.plusSeconds(1));
        assertEquals(
                305,
                jdbc.queryForObject(
                        "SELECT qty FROM stock_history WHERE line_id=1 ORDER BY recorded_at DESC"
                                + " LIMIT 1",
                        Integer.class));
        ingest(
                "as-production",
                "AS-04-1",
                "production_count",
                "CUMULATIVE",
                1001,
                now.plusSeconds(2));
        assertEquals(
                304,
                jdbc.queryForObject(
                        "SELECT qty FROM stock_history WHERE line_id=1 ORDER BY recorded_at DESC"
                                + " LIMIT 1",
                        Integer.class));
        assertEquals(
                299,
                jdbc.queryForObject(
                        "SELECT qty FROM stock_history WHERE line_id=2 ORDER BY recorded_at DESC"
                                + " LIMIT 1",
                        Integer.class));
        ingest("or-stale", "OR-06-1", "production_count", "CUMULATIVE", 999, now.minusSeconds(2));
        assertEquals(
                304,
                jdbc.queryForObject(
                        "SELECT qty FROM stock_history WHERE line_id=1 ORDER BY recorded_at DESC"
                                + " LIMIT 1",
                        Integer.class));
        jdbc.update("UPDATE data_item SET reset_time='00:00:00' WHERE data_item_id=11");
        ingest(
                "or-reset",
                "OR-06-1",
                "production_count",
                "CUMULATIVE",
                2,
                now.plusDays(1).withHour(0).withMinute(1));
        assertEquals(
                304,
                jdbc.queryForObject(
                        "SELECT qty FROM stock_history WHERE line_id=1 ORDER BY recorded_at DESC"
                                + " LIMIT 1",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT counter_epoch FROM measurement_latest WHERE data_item_id=11",
                        Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void allTwentySixBOperationsMatchCurrentOpenApiResponses() throws Exception {
        var options = new LoaderOptions();
        options.setMaxAliasesForCollections(1000);
        Map<String, Object> spec =
                new Yaml(new SafeConstructor(options))
                        .load(Files.readString(Path.of("docs/openapi.yaml")));
        var paths = (Map<String, Map<String, Object>>) spec.get("paths");
        var components = (Map<String, Object>) spec.get("components");
        var parameters = (Map<String, Map<String, Object>>) components.get("parameters");
        var schemas = (Map<String, Map<String, Object>>) components.get("schemas");
        int count = 0;
        for (var entry : paths.entrySet())
            for (var operation : entry.getValue().entrySet()) {
                if (!(operation.getValue() instanceof Map<?, ?>)) continue;
                var op = (Map<String, Object>) operation.getValue();
                String id = (String) op.get("x-api-id");
                if (id == null
                        || !Set.of("OVW", "INV", "ALM", "HST", "STA", "TLG")
                                .contains(id.split("-")[0])) continue;
                String path =
                        entry.getKey()
                                .replace("{alarmId}", "100")
                                .replace("{equipmentId}", "1")
                                .replace("{lineId}", "1");
                var servers =
                        (List<Map<String, Object>>)
                                op.getOrDefault("servers", List.of(Map.of("url", "/api/v1")));
                String actual = servers.getFirst().get("url").toString() + path;
                MockHttpServletRequestBuilder req =
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                                        org.springframework.http.HttpMethod.valueOf(
                                                operation.getKey().toUpperCase()),
                                        actual)
                                .header("Authorization", admin);
                for (var raw :
                        (List<Map<String, Object>>) op.getOrDefault("parameters", List.of())) {
                    var p =
                            raw.containsKey("$ref")
                                    ? parameters.get(
                                            raw.get("$ref")
                                                    .toString()
                                                    .substring(
                                                            raw.get("$ref")
                                                                            .toString()
                                                                            .lastIndexOf('/')
                                                                    + 1))
                                    : raw;
                    String name = (String) p.get("name");
                    if (!"query".equals(p.get("in"))) continue;
                    if (name.equals("from")) req.param(name, now.minusDays(3).toString());
                    if (name.equals("to")) req.param(name, now.plusMinutes(1).toString());
                    if (name.equals("sortBy")) req.param(name, "faultCount");
                }
                if (id.equals("ALM-006"))
                    req.contentType(MediaType.APPLICATION_JSON)
                            .content("{\"equipmentText\":\"설비1\",\"actionContent\":\"조치\"}");
                if (id.equals("TLG-005"))
                    req.contentType(MediaType.APPLICATION_JSON).content("{\"userId\":2}");
                if (id.equals("TLG-004"))
                    jdbc.update("UPDATE account SET telegram_chat_id='101' WHERE account_id=1");
                if (id.equals("TLG-007"))
                    req.header("X-Telegram-Bot-Api-Secret-Token", "v7-unit-test-callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"update_id\":99,\"message\":{\"text\":\"/start"
                                            + " link-code\",\"chat\":{\"id\":900}}}");
                var result = mvc.perform(req).andReturn();
                String status = Integer.toString(result.getResponse().getStatus());
                var responses = (Map<String, Map<String, Object>>) op.get("responses");
                assertTrue(status.startsWith("2"), id + " returned " + status);
                assertTrue(
                        responses.containsKey(status),
                        id + " response status missing from contract");
                if (!status.equals("204")) {
                    var content = (Map<String, Object>) responses.get(status).get("content");
                    var media = (Map<String, Object>) content.get("application/json");
                    assertSchema(
                            (Map<String, Object>) media.get("schema"),
                            json.readValue(result.getResponse().getContentAsString(), Object.class),
                            schemas,
                            id);
                }
                count++;
            }
        assertEquals(26, count);
    }

    @SuppressWarnings("unchecked")
    void assertSchema(
            Map<String, Object> schema,
            Object value,
            Map<String, Map<String, Object>> schemas,
            String path) {
        if (value == null) {
            assertEquals(Boolean.TRUE, schema.get("nullable"), path + " cannot be null");
            return;
        }
        if (schema.containsKey("$ref")) {
            String ref = schema.get("$ref").toString();
            assertSchema(
                    schemas.get(ref.substring(ref.lastIndexOf('/') + 1)), value, schemas, path);
            return;
        }
        if (schema.get("allOf") instanceof List<?> all)
            for (Object part : all) assertSchema((Map<String, Object>) part, value, schemas, path);
        if (schema.get("enum") instanceof List<?> values)
            assertTrue(values.contains(value), path + " enum " + value);
        if (value instanceof Map<?, ?> object) {
            for (String required : (List<String>) schema.getOrDefault("required", List.of()))
                assertTrue(object.containsKey(required), path + " missing " + required);
            var props =
                    (Map<String, Map<String, Object>>) schema.getOrDefault("properties", Map.of());
            for (var prop : props.entrySet())
                if (object.containsKey(prop.getKey()))
                    assertSchema(
                            prop.getValue(),
                            object.get(prop.getKey()),
                            schemas,
                            path + "." + prop.getKey());
        }
        if (value instanceof List<?> list && schema.get("items") instanceof Map<?, ?> items)
            for (int i = 0; i < list.size(); i++)
                assertSchema(
                        (Map<String, Object>) items, list.get(i), schemas, path + "[" + i + "]");
        if (value instanceof Number number) {
            var actual = new BigDecimal(number.toString());
            if (schema.containsKey("minimum"))
                assertTrue(
                        actual.compareTo(new BigDecimal(schema.get("minimum").toString())) >= 0,
                        path);
            if (schema.containsKey("maximum"))
                assertTrue(
                        actual.compareTo(new BigDecimal(schema.get("maximum").toString())) <= 0,
                        path);
        }
        if (value instanceof String text) {
            if (schema.containsKey("maxLength"))
                assertTrue(text.length() <= ((Number) schema.get("maxLength")).intValue(), path);
            if ("date-time".equals(schema.get("format")))
                assertDoesNotThrow(() -> OffsetDateTime.parse(text), path);
        }
    }
}

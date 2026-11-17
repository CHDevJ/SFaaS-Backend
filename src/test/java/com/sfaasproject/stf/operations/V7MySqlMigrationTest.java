package com.sfaasproject.stf.operations;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.alarm.repository.V7AlarmRepository;
import com.sfaasproject.stf.alarm.service.AlarmLifecycleService.*;
import com.sfaasproject.stf.alarm.service.RecoveryTimeEstimator;
import com.sfaasproject.stf.common.integration.JdbcV7Context;
import com.sfaasproject.stf.inventory.service.*;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@EnabledIfEnvironmentVariable(named = "STF_TEST_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V7MySqlMigrationTest {
    JdbcTemplate bootstrap, jdbc;
    JdbcBDatabase db;
    TransactionTemplate tx;
    Flyway flyway;
    V7AlarmRepository alarms;
    String database;
    long historicalAlarm, item;
    Integer v5Checksum;
    final OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);

    @BeforeAll
    void migrateExistingV5DatabaseToV7() {
        String url = System.getenv("STF_TEST_MYSQL_URL");
        if (!url.contains("/stf_b_test?"))
            throw new IllegalArgumentException("Isolated test database required");
        String password = System.getenv("STF_TEST_MYSQL_PASSWORD");
        bootstrap = new JdbcTemplate(new DriverManagerDataSource(url, "root", password));
        database = "stf_b_v7_validation_" + UUID.randomUUID().toString().replace("-", "");
        bootstrap.execute("CREATE DATABASE " + database);
        var source =
                new DriverManagerDataSource(
                        url.replace("/stf_b_test?", "/" + database + "?"), "root", password);
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        db =
                new JdbcBDatabase(
                        jdbc,
                        tx,
                        Clock.fixed(now.plusMinutes(1).toInstant(), ZoneId.of("Asia/Seoul")));
        Flyway.configure().dataSource(source).target("5").load().migrate();
        v5Checksum =
                jdbc.queryForObject(
                        "SELECT checksum FROM flyway_schema_history WHERE version='5'",
                        Integer.class);
        item =
                jdbc.queryForObject(
                        "SELECT MIN(data_item_id) FROM data_item WHERE equipment_id=1", Long.class);
        historicalAlarm =
                db.insert(
                        "INSERT INTO"
                            + " alarm(equipment_id,data_item_id,grade,message,trigger_value,limit_value,process_state,occurred_at)"
                            + " VALUES (1,?,'CRITICAL','migration history',30,20,'ACKNOWLEDGED',?)",
                        item,
                        time(now.minusMinutes(5)));
        flyway = Flyway.configure().dataSource(source).load();
        flyway.migrate();
        alarms = new V7AlarmRepository(db);
    }

    @AfterAll
    void removeOnlyThisRandomTestDatabase() {
        if (database != null && database.matches("stf_b_v7_validation_[a-f0-9]{32}"))
            bootstrap.execute("DROP DATABASE " + database);
    }

    @Test
    void v5ChecksumAndSeedRemainUnchangedAfterV6() {
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(6, flyway.info().applied().length);
        assertEquals(
                v5Checksum,
                jdbc.queryForObject(
                        "SELECT checksum FROM flyway_schema_history WHERE version='5'",
                        Integer.class));
        assertEquals(
                4,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM stock_history WHERE qty=300", Integer.class));
        assertEquals(
                "10",
                jdbc.queryForObject(
                        "SELECT setting_value FROM system_setting WHERE"
                            + " setting_key='STOCK_DANGER_MIN'",
                        String.class));
    }

    @Test
    void existingRolesAndAlarmStateConvertWithoutRemovingHistoryTables() {
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM account WHERE role IS NOT NULL AND role NOT IN"
                            + " ('ADMIN','USER')",
                        Integer.class));
        assertEquals("ACTIVE", alarms.find(historicalAlarm).get("process_state"));
        for (String table :
                List.of("duty", "duty_member", "part", "part_replacement", "backend_alarm_impact"))
            assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        assertEquals(
                100,
                jdbc.queryForObject(
                        "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE"
                            + " table_schema=DATABASE() AND table_name='account' AND"
                            + " column_name='name'",
                        Integer.class));
    }

    @Test
    void generatedActiveIndexRejectsDuplicateButClosedRowsAllowNextAlarm() {
        long dataItem = newItem("unique");
        long first = create(dataItem);
        assertThrows(DuplicateKeyException.class, () -> create(dataItem));
        tx.execute(
                s -> {
                    assertTrue(alarms.complete(first, 0, action(first)));
                    return null;
                });
        assertNotEquals(first, create(dataItem));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM alarm WHERE data_item_id=? AND"
                            + " process_state='ACTIVE'",
                        Integer.class,
                        dataItem));
    }

    @Test
    void actionCompletionAndRollbackAreAtomicOnRealMySql() {
        long alarm = create(newItem("rollback"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.execute(
                                s -> {
                                    assertTrue(alarms.complete(alarm, 0, action(alarm)));
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals("ACTIVE", alarms.find(alarm).get("process_state"));
        assertNull(alarms.action(alarm));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM alarm_log WHERE alarm_id=?", Integer.class, alarm));
        tx.execute(
                s -> {
                    assertTrue(alarms.complete(alarm, 0, action(alarm)));
                    assertFalse(alarms.complete(alarm, 0, action(alarm)));
                    return null;
                });
        assertEquals(4000, alarms.action(alarm).get("action_content").toString().length());
        assertEquals(200, alarms.action(alarm).get("equipment_text").toString().length());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM alarm_log WHERE alarm_id=?", Integer.class, alarm));
    }

    @Test
    void metadataAndForecastProjectionReadV6ColumnsOnRealMySql() {
        var context = new JdbcV7Context(db);
        assertEquals(27, context.equipment().size());
        assertEquals(5, context.lines().size());
        var service =
                new V7InventoryService(
                        db,
                        context,
                        new AssemblyForecastService(),
                        new RecoveryTimeEstimator(),
                        "DEMO_PRODUCTION");
        var result = service.snapshot();
        assertEquals(4, result.inventory().size());
        assertTrue(result.inventory().stream().allMatch(x -> x.quantity() == 300));
        assertTrue(result.lines().stream().allMatch(x -> x.expectedLossAmount() == null));
        assertEquals("INSUFFICIENT_DATA", context.counterQuality(1, now, 10));
    }

    long newItem(String suffix) {
        return db.insert(
                "INSERT INTO"
                    + " data_item(equipment_id,item_code,name,item_kind,caution_limit,critical_limit,limit_direction)"
                    + " VALUES (1,?,?,'MEASUREMENT',10,20,'ABOVE')",
                "v7_" + suffix,
                "v7 " + suffix);
    }

    long create(long dataItem) {
        return alarms.create(
                1,
                dataItem,
                "CRITICAL",
                "test",
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(20),
                now.minusMinutes(1));
    }

    Action action(long alarm) {
        return new Action(
                alarm,
                ProcessStatus.CLOSED,
                "설".repeat(200),
                "원".repeat(1000),
                "조".repeat(4000),
                now,
                new Actor(1, "관리자", Role.ADMIN),
                60);
    }
}

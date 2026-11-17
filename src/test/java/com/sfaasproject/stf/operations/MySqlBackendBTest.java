package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowDecisionService;
import com.sfaasproject.stf.duty.service.DutyScheduleDecisionService;
import com.sfaasproject.stf.operations.adapter.*;
import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.event.BEventPublisher;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.service.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

@EnabledIfEnvironmentVariable(named = "STF_TEST_MYSQL_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MySqlBackendBTest {
    JdbcTemplate jdbc;
    JdbcBDatabase db;
    JdbcBUsersSettings users;
    JdbcBAlarms alarms;
    UnitOfWork tx;
    Flyway flyway;
    final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    @BeforeAll
    void initialize() {
        String baseUrl = System.getenv("STF_TEST_MYSQL_URL");
        if (!baseUrl.contains("/stf_b_test?"))
            throw new IllegalArgumentException("Use the isolated stf_b_test container only");
        String database = "stf_b_validation_" + UUID.randomUUID().toString().replace("-", "");
        var bootstrap =
                new DriverManagerDataSource(
                        baseUrl, "root", System.getenv("STF_TEST_MYSQL_PASSWORD"));
        new JdbcTemplate(bootstrap).execute("CREATE DATABASE " + database);
        var source =
                new DriverManagerDataSource(
                        baseUrl.replace("/stf_b_test?", "/" + database + "?"),
                        "root",
                        System.getenv("STF_TEST_MYSQL_PASSWORD"));
        flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        flyway.migrate();
        jdbc = new JdbcTemplate(source);
        var template = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx =
                new UnitOfWork() {
                    public <T> T execute(Supplier<T> action) {
                        return template.execute(status -> action.get());
                    }
                };
        db = new JdbcBDatabase(jdbc, template, clock);
        users = new JdbcBUsersSettings(db);
        alarms = new JdbcBAlarms(db);
        jdbc.update(
                "INSERT INTO part(equipment_id,name,manage_basis,replace_limit,last_replaced_at)"
                        + " VALUES (1,'검증용 필터','ELAPSED_DAYS',1.5,?)",
                java.sql.Timestamp.valueOf(LocalDateTime.now(clock).minusDays(1)));
        jdbc.update(
                "INSERT INTO"
                    + " data_item(equipment_id,item_code,name,item_kind,caution_limit,critical_limit,limit_direction)"
                    + " VALUES (1,'backend_b_test_temperature','검증"
                    + " 온도','MEASUREMENT',10,20,'ABOVE')");
        long item =
                jdbc.queryForObject(
                        "SELECT data_item_id FROM data_item WHERE equipment_id=1 AND"
                                + " item_code='backend_b_test_temperature'",
                        Long.class);
        jdbc.update(
                "INSERT INTO"
                    + " alarm(equipment_id,data_item_id,grade,message,trigger_value,limit_value,occurred_at)"
                    + " VALUES (1,?,'CRITICAL','검증 알람',400,367,?)",
                item,
                java.sql.Timestamp.valueOf(LocalDateTime.now(clock).minusMinutes(20)));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void realMySqlAppliesAllThreeMigrationsAndSeed() {
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(3, flyway.info().applied().length);
        assertEquals(27, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM line", Integer.class));
        assertEquals(
                4000,
                jdbc.queryForObject(
                        "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE"
                                + " table_schema=DATABASE() AND table_name='alarm_action' AND"
                                + " column_name='action_content'",
                        Integer.class));
        assertEquals(
                1000,
                jdbc.queryForObject(
                        "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE"
                                + " table_schema=DATABASE() AND table_name='part_replacement' AND"
                                + " column_name='reason'",
                        Integer.class));
    }

    @Test
    @Order(2)
    void allReadAdaptersExecuteAgainstRealMySql() throws Exception {
        var reads = new JdbcBReadPort(db, users, users, new JdbcBInventory(db), alarms);
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
                                    } catch (Exception ex) {
                                        throw new RuntimeException(c.id(), ex);
                                    }
                                });
                new JdbcBFlowTest().validateRecord(factory.getValidator(), response, c.id());
            }
        }
    }

    @Test
    @Order(3)
    void realMySqlPersistsLongActionAndLinkedReplacement() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "1",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));
        long maint = users.maintenanceIds().iterator().next();
        var parts = new PartApplicationService(new JdbcBParts(db), users, tx);
        var commands =
                new AlarmApplicationService(
                        alarms,
                        users,
                        users,
                        tx,
                        new BEventPublisher(event -> {}),
                        new AlarmWorkflowDecisionService(),
                        parts,
                        clock);
        commands.acknowledgeAlarm(1L, new AlarmAcknowledgementRequest(maint));
        var result =
                commands.createAlarmAction(
                        1L,
                        new AlarmActionRequest(
                                "원".repeat(1000),
                                "조".repeat(4000),
                                1L,
                                OffsetDateTime.now(clock),
                                "검증용 자유 입력 사유"));
        assertNotNull(result.data().partReplacementId());
        assertNotNull(
                jdbc.queryForObject(
                        "SELECT action_id FROM part_replacement WHERE replacement_id=?",
                        Long.class,
                        result.data().partReplacementId()));
        assertEquals(
                4000,
                jdbc.queryForObject(
                        "SELECT CHAR_LENGTH(action_content) FROM alarm_action WHERE alarm_id=1",
                        Integer.class));
    }

    @Test
    @Order(4)
    void realMySqlSerializesDutyCreationAndRejectsOverlap() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "1",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));
        var duty =
                new DutyApplicationService(
                        new JdbcBDuties(db, new JdbcBClaims(db)),
                        users,
                        tx,
                        new DutyScheduleDecisionService(),
                        clock);
        var today = LocalDate.now(clock);
        long maint = users.maintenanceIds().iterator().next();
        duty.createAlarmDutySchedule(new DutyScheduleWriteRequest(today, today.plusDays(2), maint));
        assertThrows(
                com.sfaasproject.stf.common.DomainException.class,
                () ->
                        duty.createAlarmDutySchedule(
                                new DutyScheduleWriteRequest(
                                        today.plusDays(1), today.plusDays(3), maint)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM duty", Integer.class));
    }
}

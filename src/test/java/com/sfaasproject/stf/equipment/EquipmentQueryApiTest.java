package com.sfaasproject.stf.equipment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AuthTestConfig;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.telegram.service.RedisTelegramLinkTokenStore;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import com.sfaasproject.stf.user.service.TemporaryPasswordSender;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * EQP-002~005 설비 현황 조회. B 의 H2 스키마(V1 과 같은 컬럼)에 설비 상태·마지막 값·알람을 넣고
 * 상태·값 판정이 OVW-001 과 같은 규칙인지 확인한다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:equipment-queries;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import(AuthTestConfig.class)
class EquipmentQueryApiTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 18, 9, 0);

    @MockitoBean RedisTelegramLinkTokenStore telegramLinkTokenStore;
    @MockitoBean TemporaryPasswordSender temporaryPasswordSender;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;

    String token;
    long line;
    long forging;
    long turning;
    long empty;
    long running;
    long faulty;
    long silent;

    @BeforeEach
    void setUp() {
        for (String table : List.of("alarm_action", "alarm", "measurement", "measurement_latest", "data_item",
                "equipment_state", "part", "equipment", "process", "stock_history", "line", "change_log",
                "auth_token", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        AccountEntity account = AccountEntity.signup("viewer", passwords.encode("Viewer123!"), "viewer", now);
        account.approve(Permission.USER.name(), null, now);
        account = accounts.save(account);
        token = "Bearer " + jwt.issue(account.getId(), Permission.USER, clock.instant()).value();

        line = insert("INSERT INTO line(name, line_type, sort_order) VALUES ('외륜', 'PART', 1)");
        forging = insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, 1, '단조')", line);
        turning = insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, 2, '선삭')", line);
        empty = insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, 3, '열처리')", line);
        running = equipment("OR-01-1", forging);
        faulty = equipment("OR-01-2", forging);
        silent = equipment("OR-02-1", turning);
        long gone = equipment("OR-01-9", forging);
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", now, gone);
        state(running, "RUNNING", T.minusHours(1), T.plusSeconds(3));
        state(faulty, "FAULT", T.minusMinutes(5), T.plusSeconds(2));
    }

    // ---------- EQP-002 ----------

    @Test
    void processesShowAbnormalFlagAndWorstStatus() throws Exception {
        mvc.perform(get("/api/v1/lines/" + line + "/processes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].processId").value(forging))
                .andExpect(jsonPath("$.data[0].processName").value("단조"))
                .andExpect(jsonPath("$.data[0].processOrder").value(1))
                .andExpect(jsonPath("$.data[0].hasAbnormalEquipment").value(true))
                .andExpect(jsonPath("$.data[0].representativeStatus").value("FAULT"))
                // 상태 줄이 없는 설비는 통신 끊김으로 본다.
                .andExpect(jsonPath("$.data[1].representativeStatus").value("COMMUNICATION_LOST"))
                .andExpect(jsonPath("$.data[1].hasAbnormalEquipment").value(true))
                .andExpect(jsonPath("$.data[2].processId").value(empty))
                .andExpect(jsonPath("$.data[2].hasAbnormalEquipment").value(false))
                .andExpect(jsonPath("$.data[2].representativeStatus").doesNotExist());
    }

    @Test
    void processesNeedActiveLineAndLogin() throws Exception {
        long deleted = insert("INSERT INTO line(name, line_type, sort_order, deleted_at) VALUES ('폐기', 'PART', 2, ?)",
                T);
        for (long id : new long[] {deleted, 999_999L}) {
            mvc.perform(get("/api/v1/lines/" + id + "/processes").header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("LINE_NOT_FOUND"));
        }
        for (String url : List.of("/api/v1/lines/" + line + "/processes", "/api/v1/processes/" + forging + "/equipment",
                "/api/v1/equipment/" + running, "/api/v1/equipment/" + running + "/data-items")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    // ---------- EQP-003 ----------

    @Test
    void processEquipmentShowsStatusAlarmsMainAndAbnormalMeasurements() throws Exception {
        long temp = item(faulty, "temp", "MEASUREMENT", 1);
        jdbc.update("UPDATE data_item SET unit = '℃', decimal_places = 1, caution_limit = 70, critical_limit = 85,"
                + " limit_direction = 'ABOVE' WHERE data_item_id = ?", temp);
        long flow = item(faulty, "flow", "MEASUREMENT", 2);
        jdbc.update("UPDATE data_item SET caution_limit = 20, critical_limit = 10, limit_direction = 'BELOW'"
                + " WHERE data_item_id = ?", flow);
        long door = item(faulty, "door", "STATE", 3);
        jdbc.update("UPDATE data_item SET state_label_0 = '닫힘', state_label_1 = '열림', alarm_state_value = 1,"
                + " alarm_state_grade = 'CAUTION' WHERE data_item_id = ?", door);
        long count = item(faulty, "prod_count", "CUMULATIVE", 4);
        long m3 = item(faulty, "m3", "MEASUREMENT", 5);
        long m4 = item(faulty, "m4", "MEASUREMENT", 6);
        item(faulty, "never", "MEASUREMENT", 0);
        latest(temp, 90.04);
        latest(flow, 15);
        latest(door, 1);
        latest(count, 1200);
        latest(m3, 1);
        latest(m4, 2);
        alarm(faulty, temp, "ACTIVE");
        alarm(faulty, flow, "ACTIVE");
        alarm(faulty, door, "CLOSED");

        mvc.perform(get("/api/v1/processes/" + forging + "/equipment").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].equipmentId").value(running))
                .andExpect(jsonPath("$.data[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].statusStartedAt").value("2026-11-18T08:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].activeAlarmCount").value(0))
                .andExpect(jsonPath("$.data[0].mainMeasurements.length()").value(0))
                .andExpect(jsonPath("$.data[1].equipmentId").value(faulty))
                .andExpect(jsonPath("$.data[1].equipmentCode").value("OR-01-2"))
                .andExpect(jsonPath("$.data[1].lineName").value("외륜"))
                .andExpect(jsonPath("$.data[1].lineType").value("PART"))
                .andExpect(jsonPath("$.data[1].processOrder").value(1))
                .andExpect(jsonPath("$.data[1].status").value("FAULT"))
                .andExpect(jsonPath("$.data[1].activeAlarmCount").value(2))
                // 주요 측정값은 표시 순서가 앞선 측정값 3개.
                .andExpect(jsonPath("$.data[1].mainMeasurements.length()").value(3))
                .andExpect(jsonPath("$.data[1].mainMeasurements[0].dataItemId").value(temp))
                .andExpect(jsonPath("$.data[1].mainMeasurements[0].displayValue").value("90.0"))
                .andExpect(jsonPath("$.data[1].mainMeasurements[0].valueStatus").value("CRITICAL"))
                .andExpect(jsonPath("$.data[1].mainMeasurements[2].dataItemId").value(m3))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements.length()").value(3))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[0].dataItemId").value(temp))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[0].valueStatus").value("CRITICAL"))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[0].threshold").value(85.0))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[0].cautionThreshold").value(70.0))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[0].criticalThreshold").value(85.0))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[0].thresholdDirection").value("ABOVE"))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[0].decimalPlaces").value(1))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[0].unit").value("℃"))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[1].dataItemId").value(flow))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[1].valueStatus").value("CAUTION"))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[1].threshold").value(20.0))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[1].thresholdDirection").value("BELOW"))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[2].dataItemId").value(door))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[2].displayValue").value("열림"))
                .andExpect(jsonPath("$.data[1].abnormalMeasurements[2].threshold").value(1));
    }

    @Test
    void processEquipmentNeedsExistingProcessOfActiveLine() throws Exception {
        jdbc.update("UPDATE line SET deleted_at = ? WHERE line_id = ?", T, line);
        for (long id : new long[] {forging, 999_999L}) {
            mvc.perform(get("/api/v1/processes/" + id + "/equipment").header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("PROCESS_NOT_FOUND"));
        }
    }

    // ---------- EQP-004 ----------

    @Test
    void detailShowsBasicInfoStatusAndValuesByType() throws Exception {
        long temp = item(running, "temp", "MEASUREMENT", 1);
        jdbc.update("UPDATE data_item SET decimal_places = 2 WHERE data_item_id = ?", temp);
        long door = item(running, "door", "STATE", 2);
        jdbc.update("UPDATE data_item SET state_label_0 = '닫힘', state_label_1 = '열림' WHERE data_item_id = ?", door);
        long count = item(running, "prod_count", "CUMULATIVE", 3);
        latest(temp, 61.256);
        latest(door, 0);
        latest(count, 42);

        mvc.perform(get("/api/v1/equipment/" + running).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.basicInfo.equipmentId").value(running))
                .andExpect(jsonPath("$.data.basicInfo.equipmentCode").value("OR-01-1"))
                .andExpect(jsonPath("$.data.basicInfo.equipmentType").value("시험기"))
                .andExpect(jsonPath("$.data.basicInfo.lineName").value("외륜"))
                .andExpect(jsonPath("$.data.basicInfo.processName").value("단조"))
                .andExpect(jsonPath("$.data.basicInfo.communicationMethod").value("MODBUS_TCP"))
                .andExpect(jsonPath("$.data.basicInfo.photoUrl").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.statusStartedAt").value("2026-11-18T08:00:00+09:00"))
                .andExpect(jsonPath("$.data.lastReceivedAt").value("2026-11-18T09:00:03+09:00"))
                .andExpect(jsonPath("$.data.measurements.length()").value(1))
                .andExpect(jsonPath("$.data.measurements[0].displayValue").value("61.26"))
                .andExpect(jsonPath("$.data.measurements[0].valueStatus").value("NORMAL"))
                .andExpect(jsonPath("$.data.stateValues[0].dataItemId").value(door))
                .andExpect(jsonPath("$.data.stateValues[0].displayValue").value("닫힘"))
                .andExpect(jsonPath("$.data.cumulativeValues[0].displayValue").value("42"))
                .andExpect(jsonPath("$.data.cumulativeValues[0].valueStatus").doesNotExist())
                .andExpect(jsonPath("$.data.cumulativeValues[0].measuredAt").value("2026-11-18T09:00:00+09:00"));
    }

    @Test
    void detailAndDataItemsNeedActiveEquipment() throws Exception {
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", T, silent);
        for (long id : new long[] {silent, 999_999L}) {
            for (String url : List.of("/api/v1/equipment/" + id, "/api/v1/equipment/" + id + "/data-items")) {
                mvc.perform(get(url).header("Authorization", token))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error.code").value("EQUIPMENT_NOT_FOUND"));
            }
        }
    }

    // ---------- EQP-005 ----------

    @Test
    void dataItemsListReceivedItemsInDisplayOrder() throws Exception {
        long second = item(running, "b", "MEASUREMENT", 2);
        long first = item(running, "a", "MEASUREMENT", 1);
        item(running, "never", "MEASUREMENT", 0);
        latest(second, 2);
        latest(first, 1);

        mvc.perform(get("/api/v1/equipment/" + running + "/data-items").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].dataItemId").value(first))
                .andExpect(jsonPath("$.data[0].valueType").value("MEASUREMENT"))
                .andExpect(jsonPath("$.data[0].value").value(Matchers.closeTo(1.0, 1e-9)))
                .andExpect(jsonPath("$.data[1].dataItemId").value(second));
    }

    // ---------- helpers ----------

    private long equipment(String code, long processId) {
        return insert("INSERT INTO equipment(equip_code, name, equip_type, process_id, comm_type)"
                + " VALUES (?, ?, '시험기', ?, 'MODBUS_TCP')", code, code + " 설비", processId);
    }

    private void state(long equipmentId, String status, LocalDateTime since, LocalDateTime received) {
        jdbc.update("INSERT INTO equipment_state(equipment_id, status, status_since, last_received_at)"
                + " VALUES (?, ?, ?, ?)", equipmentId, status, since, received);
    }

    private long item(long equipmentId, String code, String kind, int sortOrder) {
        return insert("INSERT INTO data_item(equipment_id, item_code, name, item_kind, sort_order, decimal_places)"
                + " VALUES (?, ?, ?, ?, ?, ?)", equipmentId, code, code + " 이름", kind, sortOrder,
                "MEASUREMENT".equals(kind) ? 1 : 0);
    }

    private void latest(long dataItemId, double value) {
        jdbc.update("INSERT INTO measurement_latest(data_item_id, measured_value, measured_at, received_at)"
                + " VALUES (?, ?, ?, ?)", dataItemId, value, T, T.plusSeconds(1));
    }

    private void alarm(long equipmentId, long dataItemId, String state) {
        insert("INSERT INTO alarm(equipment_id, data_item_id, grade, message, trigger_value, limit_value,"
                + " process_state, occurred_at) VALUES (?, ?, 'CAUTION', '시험 알람', 1, 2, ?, ?)",
                equipmentId, dataItemId, state, T);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keys);
        return ((Number) keys.getKeyList().get(0).values().iterator().next()).longValue();
    }
}

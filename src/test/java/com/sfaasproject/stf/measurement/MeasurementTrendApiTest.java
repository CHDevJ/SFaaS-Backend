package com.sfaasproject.stf.measurement;

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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** MEAS-001 데이터 항목 추세. B 의 H2 스키마(V1 과 같은 컬럼)로 원본·집계·끊김·알람을 확인한다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:measurement-trend;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import(AuthTestConfig.class)
class MeasurementTrendApiTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 18, 9, 0);
    private static final String URL = "/api/v1/equipment/%d/measurements";

    @MockitoBean RedisTelegramLinkTokenStore telegramLinkTokenStore;
    @MockitoBean TemporaryPasswordSender temporaryPasswordSender;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;

    String token;
    long equipmentId;
    long otherEquipmentId;
    long temp;
    long door;

    @BeforeEach
    void setUp() {
        for (String table : List.of("alarm_action", "alarm", "measurement", "measurement_latest", "data_item",
                "equipment_status_history", "equipment_state", "part", "equipment", "process", "stock_history", "line",
                "change_log", "auth_token", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        AccountEntity account = AccountEntity.signup("trend", passwords.encode("Trend1234!"), "trend", now);
        account.approve(Permission.USER.name(), null, now);
        account = accounts.save(account);
        token = "Bearer " + jwt.issue(account.getId(), Permission.USER, clock.instant()).value();

        long line = insert("INSERT INTO line(name, line_type, sort_order) VALUES ('외륜', 'PART', 1)");
        long process = insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, 1, '단조')", line);
        equipmentId = insert("INSERT INTO equipment(equip_code, name, equip_type, process_id, comm_type)"
                + " VALUES ('OR-01-1', '단조기', '시험기', ?, 'MQTT')", process);
        otherEquipmentId = insert("INSERT INTO equipment(equip_code, name, equip_type, process_id, comm_type)"
                + " VALUES ('OR-01-2', '단조기 2', '시험기', ?, 'MQTT')", process);
        temp = insert("INSERT INTO data_item(equipment_id, item_code, name, item_kind, unit, decimal_places,"
                + " caution_limit, critical_limit, limit_direction, collect_cycle_sec)"
                + " VALUES (?, 'temp', '온도', 'MEASUREMENT', '℃', 1, 70, 85, 'ABOVE', 3)", equipmentId);
        door = insert("INSERT INTO data_item(equipment_id, item_code, name, item_kind, decimal_places, state_label_0,"
                + " state_label_1, collect_cycle_sec) VALUES (?, 'door', '문', 'STATE', 0, '닫힘', '열림', 3)",
                equipmentId);
        jdbc.update("INSERT INTO equipment_state(equipment_id, status, status_since, last_received_at)"
                + " VALUES (?, 'RUNNING', ?, ?)", equipmentId, T, T);
    }

    @Test
    void shortRangeReturnsRawPointsWithGapsAlarmsAndMetadata() throws Exception {
        measurement(temp, T.plusMinutes(1), 60);
        measurement(temp, T.plusMinutes(2), 72);
        measurement(temp, T.plusMinutes(20), 90);
        measurement(temp, T.minusMinutes(1), 1);
        measurement(door, T.plusMinutes(1), 1);
        statusHistory("COMMUNICATION_LOST", T.plusMinutes(5), T.plusMinutes(15));
        statusHistory("COMMUNICATION_LOST", T.minusHours(3), T.minusHours(2));
        alarm(temp, T.plusMinutes(2), T.plusMinutes(10), 72, "CAUTION");
        alarm(temp, T.minusMinutes(30), null, 88, "CRITICAL");

        mvc.perform(trend(equipmentId, T, T.plusHours(1), temp + "," + door))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipmentId").value(equipmentId))
                .andExpect(jsonPath("$.data.currentlyConnected").value(true))
                .andExpect(jsonPath("$.data.disconnections.length()").value(1))
                .andExpect(jsonPath("$.data.disconnections[0].startedAt").value("2026-11-18T09:05:00+09:00"))
                .andExpect(jsonPath("$.data.disconnections[0].endedAt").value("2026-11-18T09:15:00+09:00"))
                .andExpect(jsonPath("$.data.series.length()").value(2))
                .andExpect(jsonPath("$.data.series[0].dataItemId").value(temp))
                .andExpect(jsonPath("$.data.series[0].dataItemName").value("온도"))
                .andExpect(jsonPath("$.data.series[0].valueType").value("MEASUREMENT"))
                .andExpect(jsonPath("$.data.series[0].unit").value("℃"))
                .andExpect(jsonPath("$.data.series[0].cautionThreshold").value(70.0))
                .andExpect(jsonPath("$.data.series[0].criticalThreshold").value(85.0))
                .andExpect(jsonPath("$.data.series[0].thresholdDirection").value("ABOVE"))
                .andExpect(jsonPath("$.data.series[0].decimalPlaces").value(1))
                .andExpect(jsonPath("$.data.series[0].bucketSeconds").value(3))
                // 원본 3점 + 끊김 시작의 null 점, 시각순.
                .andExpect(jsonPath("$.data.series[0].points.length()").value(4))
                .andExpect(jsonPath("$.data.series[0].points[0].measuredAt").value("2026-11-18T09:01:00+09:00"))
                .andExpect(jsonPath("$.data.series[0].points[0].value").value(60.0))
                .andExpect(jsonPath("$.data.series[0].points[2].measuredAt").value("2026-11-18T09:05:00+09:00"))
                .andExpect(jsonPath("$.data.series[0].points[2].value").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.series[0].points[3].value").value(90.0))
                .andExpect(jsonPath("$.data.series[0].alarmMarkers.length()").value(1))
                .andExpect(jsonPath("$.data.series[0].alarmMarkers[0].value").value(72.0))
                .andExpect(jsonPath("$.data.series[0].alarmMarkers[0].grade").value("CAUTION"))
                // 구간 전에 발생해 아직 열린 알람은 구간 시작부터 이상 구간이다.
                .andExpect(jsonPath("$.data.series[0].abnormalIntervals.length()").value(2))
                .andExpect(jsonPath("$.data.series[0].abnormalIntervals[0].startedAt").value("2026-11-18T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.series[0].abnormalIntervals[0].endedAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.series[0].abnormalIntervals[0].grade").value("CRITICAL"))
                .andExpect(jsonPath("$.data.series[0].abnormalIntervals[1].endedAt").value("2026-11-18T09:10:00+09:00"))
                .andExpect(jsonPath("$.data.series[0].disconnections.length()").value(1))
                .andExpect(jsonPath("$.data.series[1].dataItemId").value(door))
                .andExpect(jsonPath("$.data.series[1].zeroDisplayName").value("닫힘"))
                .andExpect(jsonPath("$.data.series[1].oneDisplayName").value("열림"))
                .andExpect(jsonPath("$.data.series[1].cautionThreshold").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.series[1].points[0].value").value(1.0));
    }

    @Test
    void sevenDayRangeAveragesMeasurementsAndKeepsLastStateInTenMinuteBuckets() throws Exception {
        measurement(temp, T.plusMinutes(1), 10);
        measurement(temp, T.plusMinutes(9), 30);
        measurement(temp, T.plusMinutes(11), 50);
        measurement(door, T.plusMinutes(1), 0);
        measurement(door, T.plusMinutes(8), 1);

        mvc.perform(trend(equipmentId, T, T.plusDays(7), temp + "," + door))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.series[0].bucketSeconds").value(600))
                .andExpect(jsonPath("$.data.series[0].points.length()").value(2))
                .andExpect(jsonPath("$.data.series[0].points[0].measuredAt").value("2026-11-18T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.series[0].points[0].value").value(20.0))
                .andExpect(jsonPath("$.data.series[0].points[1].measuredAt").value("2026-11-18T09:10:00+09:00"))
                .andExpect(jsonPath("$.data.series[0].points[1].value").value(50.0))
                .andExpect(jsonPath("$.data.series[1].points.length()").value(1))
                .andExpect(jsonPath("$.data.series[1].points[0].value").value(1.0));
    }

    @Test
    void rawRangeWithMoreThan2000PointsFallsBackToOneMinuteBuckets() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 2400; i++) {
            rows.add(new Object[] {temp, Timestamp.valueOf(T.plusSeconds(3L * i)), (double) i});
        }
        jdbc.batchUpdate("INSERT INTO measurement(data_item_id, measured_at, measured_value) VALUES (?, ?, ?)", rows);

        mvc.perform(trend(equipmentId, T, T.plusHours(2), String.valueOf(temp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.series[0].bucketSeconds").value(60))
                .andExpect(jsonPath("$.data.series[0].points.length()").value(120))
                // 첫 1분 칸은 0~19 의 평균.
                .andExpect(jsonPath("$.data.series[0].points[0].value").value(9.5));
    }

    @Test
    void ongoingDisconnectionStaysOpenAndEquipmentIsDisconnected() throws Exception {
        jdbc.update("UPDATE equipment_state SET status = 'COMMUNICATION_LOST' WHERE equipment_id = ?", equipmentId);
        statusHistory("COMMUNICATION_LOST", T.plusMinutes(30), null);
        mvc.perform(trend(equipmentId, T, T.plusHours(1), String.valueOf(temp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentlyConnected").value(false))
                .andExpect(jsonPath("$.data.disconnections[0].startedAt").value("2026-11-18T09:30:00+09:00"))
                .andExpect(jsonPath("$.data.disconnections[0].endedAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.series[0].points.length()").value(1))
                .andExpect(jsonPath("$.data.series[0].points[0].value").value(Matchers.nullValue()));
    }

    @Test
    void validatesPeriodItemsAndOwnership() throws Exception {
        mvc.perform(trend(equipmentId, T, T, String.valueOf(temp)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_PERIOD"));
        mvc.perform(trend(equipmentId, T.plusHours(1), T, String.valueOf(temp)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_PERIOD"));
        StringBuilder eleven = new StringBuilder();
        for (int i = 1; i <= 11; i++) {
            eleven.append(i == 1 ? "" : ",").append(i);
        }
        for (String ids : List.of(eleven.toString(), temp + "," + temp, "abc")) {
            mvc.perform(trend(equipmentId, T, T.plusHours(1), ids))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get(URL.formatted(equipmentId)).param("from", "2026-11-18T09:00:00+09:00")
                        .param("to", "2026-11-18T10:00:00+09:00").header("Authorization", token))
                .andExpect(status().isBadRequest());

        long otherItem = insert("INSERT INTO data_item(equipment_id, item_code, name) VALUES (?, 'temp', '온도')",
                otherEquipmentId);
        for (long id : new long[] {otherItem, 999_999L}) {
            mvc.perform(trend(equipmentId, T, T.plusHours(1), temp + "," + id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("DATA_ITEM_NOT_FOUND"));
        }
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", T, otherEquipmentId);
        mvc.perform(trend(otherEquipmentId, T, T.plusHours(1), String.valueOf(otherItem)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EQUIPMENT_NOT_FOUND"));
        mvc.perform(get(URL.formatted(equipmentId)).param("from", "2026-11-18T09:00:00+09:00")
                        .param("to", "2026-11-18T10:00:00+09:00").param("dataItemIds", String.valueOf(temp)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private MockHttpServletRequestBuilder trend(long equipment, LocalDateTime from, LocalDateTime to, String ids) {
        return get(URL.formatted(equipment)).param("from", kst(from)).param("to", kst(to))
                .param("dataItemIds", ids).header("Authorization", token);
    }

    private static String kst(LocalDateTime time) {
        return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "+09:00";
    }

    private void measurement(long dataItemId, LocalDateTime at, double value) {
        jdbc.update("INSERT INTO measurement(data_item_id, measured_at, measured_value) VALUES (?, ?, ?)",
                dataItemId, at, value);
    }

    private void statusHistory(String status, LocalDateTime started, LocalDateTime ended) {
        jdbc.update("INSERT INTO equipment_status_history(equipment_id, status, started_at, ended_at)"
                + " VALUES (?, ?, ?, ?)", equipmentId, status, started, ended);
    }

    private void alarm(long dataItemId, LocalDateTime occurred, LocalDateTime closed, double value, String grade) {
        insert("INSERT INTO alarm(equipment_id, data_item_id, grade, message, trigger_value, limit_value,"
                        + " process_state, occurred_at, closed_at) VALUES (?, ?, ?, '시험 알람', ?, 70, ?, ?, ?)",
                equipmentId, dataItemId, grade, value, closed == null ? "ACTIVE" : "CLOSED", occurred, closed);
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

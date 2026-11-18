package com.sfaasproject.stf.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MST-010~013 설비 데이터 항목과 알람 기준. B 의 H2 스키마(V1 과 같은 컬럼)를 써서
 * 마지막 값(measurement_latest)·측정 이력(measurement)·알람(alarm)으로 만든 통계까지 확인한다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:master-data-items;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import(AuthTestConfig.class)
class MasterDataItemApiTest {

    private static final String MASTER = "/api/v1/master";

    @MockitoBean RedisTelegramLinkTokenStore telegramLinkTokenStore;
    @MockitoBean TemporaryPasswordSender temporaryPasswordSender;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;

    AccountEntity user;
    String token;
    long equipmentId;
    long otherEquipmentId;

    @BeforeEach
    void setUp() {
        for (String table : List.of("alarm_action", "alarm", "measurement", "measurement_latest", "data_item",
                "equipment_state", "part", "equipment", "process", "stock_history", "line", "change_log",
                "auth_token", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        user = approved("itemuser", Permission.USER);
        token = bearer(user);
        long line = insert("INSERT INTO line(name, line_type, sort_order) VALUES ('외륜', 'PART', 1)");
        long process = insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, 1, '단조')", line);
        equipmentId = equipment("OR-01-1", process);
        otherEquipmentId = equipment("OR-01-2", process);
    }

    // ---------- MST-010 ----------

    @Test
    void listShowsItemsInOrderWithLatestValueThresholdsAndStatistics() throws Exception {
        long temp = item(equipmentId, "spindle_temp", "MEASUREMENT", 2);
        long door = item(equipmentId, "door_open", "STATE", 1);
        long count = item(equipmentId, "prod_count", "CUMULATIVE", 3);
        long removed = item(equipmentId, "old_item", "MEASUREMENT", 0);
        jdbc.update("UPDATE data_item SET deleted_at = ? WHERE data_item_id = ?", now(), removed);
        jdbc.update("UPDATE data_item SET unit = '℃', decimal_places = 2, caution_limit = 70, critical_limit = 85,"
                + " limit_direction = 'ABOVE' WHERE data_item_id = ?", temp);
        jdbc.update("UPDATE data_item SET decimal_places = 0, state_label_0 = '닫힘', state_label_1 = '열림',"
                + " alarm_state_value = 1, alarm_state_grade = 'CRITICAL' WHERE data_item_id = ?", door);

        LocalDateTime windowStart = now().toLocalDate().minusDays(6).atStartOfDay();
        measurement(temp, windowStart.plusSeconds(1), 60);
        measurement(temp, windowStart.minusSeconds(1), 10);
        measurement(temp, now().minusMinutes(1), 80);
        latest(temp, 80, LocalDateTime.of(2026, 11, 18, 9, 0, 3));
        measurement(door, now().minusMinutes(1), 1);
        alarm(temp, now().minusDays(29), "CLOSED");
        alarm(temp, now().minusDays(31), "CLOSED");
        alarm(door, now().minusHours(1), "CLOSED");

        mvc.perform(get(MASTER + "/equipment/" + equipmentId + "/data-items").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].dataItemId").value(door))
                .andExpect(jsonPath("$.data[0].dataItemCode").value("door_open"))
                .andExpect(jsonPath("$.data[0].valueType").value("STATE"))
                .andExpect(jsonPath("$.data[0].zeroDisplayName").value("닫힘"))
                .andExpect(jsonPath("$.data[0].oneDisplayName").value("열림"))
                .andExpect(jsonPath("$.data[0].stateAlarmCondition.alarmValue").value(1))
                .andExpect(jsonPath("$.data[0].stateAlarmCondition.grade").value("CRITICAL"))
                .andExpect(jsonPath("$.data[0].measurementThreshold").doesNotExist())
                .andExpect(jsonPath("$.data[0].statistics.recentAverage").doesNotExist())
                .andExpect(jsonPath("$.data[0].statistics.alarmCountLast30Days").value(1))
                .andExpect(jsonPath("$.data[0].latestValue").doesNotExist())
                .andExpect(jsonPath("$.data[1].dataItemId").value(temp))
                .andExpect(jsonPath("$.data[1].dataItemName").value("spindle_temp 이름"))
                .andExpect(jsonPath("$.data[1].unit").value("℃"))
                .andExpect(jsonPath("$.data[1].collectionIntervalSeconds").value(3))
                .andExpect(jsonPath("$.data[1].decimalPlaces").value(2))
                .andExpect(jsonPath("$.data[1].latestValue").value(80.0))
                .andExpect(jsonPath("$.data[1].lastReceivedAt").value("2026-11-18T09:00:03+09:00"))
                .andExpect(jsonPath("$.data[1].measurementThreshold.cautionValue").value(70.0))
                .andExpect(jsonPath("$.data[1].measurementThreshold.criticalValue").value(85.0))
                .andExpect(jsonPath("$.data[1].measurementThreshold.direction").value("ABOVE"))
                .andExpect(jsonPath("$.data[1].stateAlarmCondition").doesNotExist())
                .andExpect(jsonPath("$.data[1].statistics.recentMinimum").value(60.0))
                .andExpect(jsonPath("$.data[1].statistics.recentAverage").value(70.0))
                .andExpect(jsonPath("$.data[1].statistics.recentMaximum").value(80.0))
                .andExpect(jsonPath("$.data[1].statistics.alarmCountLast30Days").value(1))
                .andExpect(jsonPath("$.data[2].dataItemId").value(count))
                .andExpect(jsonPath("$.data[2].statistics.alarmCountLast30Days").value(0));
    }

    @Test
    void listIsEmptyForUnknownOrDeletedEquipmentAndNeedsLogin() throws Exception {
        item(otherEquipmentId, "spindle_temp", "MEASUREMENT", 1);
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", now(), otherEquipmentId);
        for (long id : new long[] {otherEquipmentId, 999_999L}) {
            mvc.perform(get(MASTER + "/equipment/" + id + "/data-items").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
        mvc.perform(get(MASTER + "/equipment/" + equipmentId + "/data-items")).andExpect(status().isUnauthorized());
        mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), measurementBody("a", null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(put(MASTER + "/data-items/1"), measurementBody("a", null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(put(MASTER + "/data-items/1/threshold"), "{\"alarmValue\":1,\"grade\":\"CAUTION\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- MST-011 ----------

    @Test
    void createMeasurementItemStoresThresholdAndRecordsChange() throws Exception {
        item(equipmentId, "existing", "MEASUREMENT", 4);
        String body = measurementBody("coolant_flow",
                "{\"cautionValue\":20,\"criticalValue\":10,\"direction\":\"BELOW\"}");

        mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), body)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dataItemCode").value("coolant_flow"))
                .andExpect(jsonPath("$.data.dataItemName").value("냉각수 유량"))
                .andExpect(jsonPath("$.data.valueType").value("MEASUREMENT"))
                .andExpect(jsonPath("$.data.unit").value("L/min"))
                .andExpect(jsonPath("$.data.decimalPlaces").value(1))
                .andExpect(jsonPath("$.data.measurementThreshold.direction").value("BELOW"))
                .andExpect(jsonPath("$.data.statistics.alarmCountLast30Days").value(0))
                .andExpect(jsonPath("$.data.latestValue").doesNotExist());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM data_item WHERE item_code = 'coolant_flow'");
        assertThat(row.get("item_kind")).isEqualTo("MEASUREMENT");
        assertThat(row.get("item_role")).isEqualTo("NONE");
        assertThat(((Number) row.get("caution_limit")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) row.get("critical_limit")).doubleValue()).isEqualTo(10.0);
        assertThat(row.get("limit_direction")).isEqualTo("BELOW");
        assertThat(((Number) row.get("collect_cycle_sec")).intValue()).isEqualTo(3);
        assertThat(((Number) row.get("sort_order")).intValue()).isEqualTo(5);
        assertThat(changes()).containsExactly("DATA_ITEM|OR-01-1/coolant_flow|항목 등록|null|냉각수 유량|" + user.getId());
    }

    @Test
    void createStateAndCumulativeItems() throws Exception {
        mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), """
                        {"dataItemCode":"door_open","dataItemName":"문 열림","valueType":"STATE",
                         "collectionIntervalSeconds":3,"zeroDisplayName":"닫힘","oneDisplayName":"열림",
                         "stateAlarmCondition":{"alarmValue":1,"grade":"CAUTION"}}""")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.decimalPlaces").value(0))
                .andExpect(jsonPath("$.data.stateAlarmCondition.grade").value("CAUTION"));
        mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), """
                        {"dataItemCode":"prod_count","dataItemName":"생산 수","valueType":"CUMULATIVE",
                         "unit":"개","collectionIntervalSeconds":3}""")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.measurementThreshold").doesNotExist())
                .andExpect(jsonPath("$.data.stateAlarmCondition").doesNotExist());
        assertThat(jdbc.queryForList("SELECT alarm_state_value FROM data_item WHERE item_code = 'door_open'",
                Integer.class)).containsExactly(1);
    }

    @Test
    void createValidatesFieldsForEachValueType() throws Exception {
        List<String> invalid = List.of(
                measurementBody(null, null),
                measurementBody("Coolant", null),
                measurementBody("1flow", null),
                measurementBody("c".repeat(51), null),
                measurementBody("flow", "{\"cautionValue\":20,\"criticalValue\":10}"),
                measurementBody("flow", "{\"cautionValue\":20,\"criticalValue\":10,\"direction\":\"UP\"}"),
                """
                {"dataItemCode":"flow","dataItemName":"유량","valueType":"MEASUREMENT","collectionIntervalSeconds":3,
                 "zeroDisplayName":"닫힘"}""",
                """
                {"dataItemCode":"flow","dataItemName":"유량","valueType":"MEASUREMENT","collectionIntervalSeconds":3,
                 "stateAlarmCondition":{"alarmValue":1,"grade":"CAUTION"}}""",
                """
                {"dataItemCode":"flow","dataItemName":"유량","valueType":"MEASUREMENT","collectionIntervalSeconds":0}""",
                """
                {"dataItemCode":"flow","dataItemName":"유량","valueType":"MEASUREMENT","collectionIntervalSeconds":3,
                 "decimalPlaces":11}""",
                """
                {"dataItemCode":"flow","dataItemName":" ","valueType":"MEASUREMENT","collectionIntervalSeconds":3}""",
                """
                {"dataItemCode":"flow","dataItemName":"유량","valueType":"TEXT","collectionIntervalSeconds":3}""",
                """
                {"dataItemCode":"door","dataItemName":"문","valueType":"STATE","collectionIntervalSeconds":3,
                 "unit":"개"}""",
                """
                {"dataItemCode":"door","dataItemName":"문","valueType":"STATE","collectionIntervalSeconds":3,
                 "stateAlarmCondition":{"alarmValue":2,"grade":"CAUTION"}}""",
                """
                {"dataItemCode":"door","dataItemName":"문","valueType":"STATE","collectionIntervalSeconds":3,
                 "measurementThreshold":{"cautionValue":1,"criticalValue":2,"direction":"ABOVE"}}""",
                """
                {"dataItemCode":"cnt","dataItemName":"수","valueType":"CUMULATIVE","collectionIntervalSeconds":3,
                 "stateAlarmCondition":{"alarmValue":1,"grade":"CAUTION"}}""",
                """
                {"dataItemCode":"cnt","dataItemName":"수","valueType":"CUMULATIVE","collectionIntervalSeconds":3,
                 "decimalPlaces":2}""",
                "not-json");
        for (String body : invalid) {
            mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), body)
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(count("data_item")).isZero();
    }

    @Test
    void createRejectsThresholdOrderThatDoesNotMatchDirection() throws Exception {
        for (String threshold : List.of(
                "{\"cautionValue\":85,\"criticalValue\":70,\"direction\":\"ABOVE\"}",
                "{\"cautionValue\":10,\"criticalValue\":20,\"direction\":\"BELOW\"}",
                "{\"cautionValue\":10,\"criticalValue\":10,\"direction\":\"ABOVE\"}")) {
            mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"),
                            measurementBody("flow", threshold)).header("Authorization", token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("INVALID_THRESHOLD_ORDER"));
        }
        assertThat(count("data_item")).isZero();
    }

    @Test
    void createRejectsCodeUsedInSameEquipmentEvenIfDeleted() throws Exception {
        item(equipmentId, "flow", "MEASUREMENT", 1);
        long gone = item(equipmentId, "old", "MEASUREMENT", 2);
        jdbc.update("UPDATE data_item SET deleted_at = ? WHERE data_item_id = ?", now(), gone);
        for (String code : List.of("flow", "old")) {
            mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), measurementBody(code, null))
                            .header("Authorization", token))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("DATA_ITEM_CODE_DUPLICATE"));
        }
        mvc.perform(json(post(MASTER + "/equipment/" + otherEquipmentId + "/data-items"), measurementBody("flow", null))
                        .header("Authorization", token))
                .andExpect(status().isCreated());
    }

    @Test
    void lengthsFollowErdColumns() throws Exception {
        // 이름 50, 단위 20, 0/1 표시 20(ERD data_item, API_SPEC 11.0).
        for (String body : List.of(
                """
                {"dataItemCode":"flow","dataItemName":"%s","valueType":"MEASUREMENT","collectionIntervalSeconds":3}"""
                        .formatted("가".repeat(51)),
                """
                {"dataItemCode":"flow","dataItemName":"유량","valueType":"MEASUREMENT","unit":"%s",
                 "collectionIntervalSeconds":3}""".formatted("u".repeat(21)),
                """
                {"dataItemCode":"door","dataItemName":"문","valueType":"STATE","collectionIntervalSeconds":3,
                 "zeroDisplayName":"%s"}""".formatted("닫".repeat(21)))) {
            mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), body)
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), """
                        {"dataItemCode":"door","dataItemName":"%s","valueType":"STATE","collectionIntervalSeconds":3,
                         "zeroDisplayName":"%s","oneDisplayName":"%s"}"""
                        .formatted("가".repeat(50), "닫".repeat(20), "열".repeat(20)))
                        .header("Authorization", token))
                .andExpect(status().isCreated());
    }

    @Test
    void createNeedsActiveEquipment() throws Exception {
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", now(), otherEquipmentId);
        for (long id : new long[] {otherEquipmentId, 999_999L}) {
            mvc.perform(json(post(MASTER + "/equipment/" + id + "/data-items"), measurementBody("flow", null))
                            .header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("EQUIPMENT_NOT_FOUND"));
        }
        assertThat(count("data_item")).isZero();
    }

    // ---------- MST-012 ----------

    @Test
    void updateReplacesItemKeepsCodeAndRecordsOnlyChangedFields() throws Exception {
        long id = item(equipmentId, "flow", "MEASUREMENT", 1);
        jdbc.update("UPDATE data_item SET unit = 'L/min', caution_limit = 20, critical_limit = 10,"
                + " limit_direction = 'BELOW' WHERE data_item_id = ?", id);

        mvc.perform(json(put(MASTER + "/data-items/" + id), """
                        {"dataItemCode":"hacked","dataItemName":"냉각수 유량 A","valueType":"MEASUREMENT",
                         "unit":"L/min","collectionIntervalSeconds":5,"decimalPlaces":1,
                         "measurementThreshold":{"cautionValue":25,"criticalValue":10,"direction":"BELOW"}}""")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataItemCode").value("flow"))
                .andExpect(jsonPath("$.data.dataItemName").value("냉각수 유량 A"))
                .andExpect(jsonPath("$.data.collectionIntervalSeconds").value(5))
                .andExpect(jsonPath("$.data.measurementThreshold.cautionValue").value(25.0));
        String actor = "|" + user.getId();
        assertThat(changes()).containsExactly(
                "DATA_ITEM|OR-01-1/flow|이름|flow 이름|냉각수 유량 A" + actor,
                "DATA_ITEM|OR-01-1/flow|수집 주기|3|5" + actor,
                "THRESHOLD|OR-01-1/flow|주의 기준|20|25" + actor);

        // 기준 칸을 보내지 않으면 기존 기준을 유지한다.
        mvc.perform(json(put(MASTER + "/data-items/" + id), """
                        {"dataItemName":"냉각수 유량 B","valueType":"MEASUREMENT","unit":"L/min",
                         "collectionIntervalSeconds":5,"decimalPlaces":1}""")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.measurementThreshold.cautionValue").value(25.0))
                .andExpect(jsonPath("$.data.measurementThreshold.direction").value("BELOW"));
        assertThat(changes()).hasSize(4).last().isEqualTo("DATA_ITEM|OR-01-1/flow|이름|냉각수 유량 A|냉각수 유량 B" + actor);

        // null 을 보내면 지운다.
        mvc.perform(json(put(MASTER + "/data-items/" + id), """
                        {"dataItemName":"냉각수 유량 B","valueType":"MEASUREMENT","unit":"L/min",
                         "collectionIntervalSeconds":5,"decimalPlaces":1,"measurementThreshold":null}""")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.measurementThreshold").doesNotExist());
        assertThat(jdbc.queryForMap("SELECT caution_limit, critical_limit, limit_direction FROM data_item"
                + " WHERE data_item_id = ?", id).values()).containsOnlyNulls();
        assertThat(changes()).hasSize(7);
    }

    @Test
    void updateKeepsStateAlarmWhenOmitted() throws Exception {
        long door = item(equipmentId, "door", "STATE", 1);
        jdbc.update("UPDATE data_item SET alarm_state_value = 1, alarm_state_grade = 'CRITICAL' WHERE data_item_id = ?",
                door);
        mvc.perform(json(put(MASTER + "/data-items/" + door), """
                        {"dataItemName":"문 열림","valueType":"STATE","collectionIntervalSeconds":3}""")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stateAlarmCondition.alarmValue").value(1))
                .andExpect(jsonPath("$.data.stateAlarmCondition.grade").value("CRITICAL"));
        mvc.perform(json(put(MASTER + "/data-items/" + door), """
                        {"dataItemName":"문 열림","valueType":"STATE","collectionIntervalSeconds":3,
                         "stateAlarmCondition":null}""")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stateAlarmCondition").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT alarm_state_value FROM data_item WHERE data_item_id = ?",
                Integer.class, door)).isNull();
    }

    @Test
    void updateRejectsValueTypeChangeAndMissingItem() throws Exception {
        long id = item(equipmentId, "flow", "MEASUREMENT", 1);
        mvc.perform(json(put(MASTER + "/data-items/" + id), """
                        {"dataItemName":"유량","valueType":"STATE","collectionIntervalSeconds":3}""")
                        .header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DATA_ITEM_VALUE_TYPE_IMMUTABLE"));

        long gone = item(equipmentId, "old", "MEASUREMENT", 2);
        jdbc.update("UPDATE data_item SET deleted_at = ? WHERE data_item_id = ?", now(), gone);
        long ofDeletedEquipment = item(otherEquipmentId, "flow", "MEASUREMENT", 1);
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", now(), otherEquipmentId);
        for (long missing : new long[] {gone, ofDeletedEquipment, 999_999L}) {
            mvc.perform(json(put(MASTER + "/data-items/" + missing), measurementBody(null, null))
                            .header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("DATA_ITEM_NOT_FOUND"));
        }
        assertThat(changes()).isEmpty();
    }

    // ---------- MST-013 ----------

    @Test
    void thresholdUpdateChangesMeasurementLimits() throws Exception {
        long id = item(equipmentId, "temp", "MEASUREMENT", 1);
        mvc.perform(json(put(MASTER + "/data-items/" + id + "/threshold"),
                        "{\"cautionValue\":70.5,\"criticalValue\":85,\"direction\":\"ABOVE\"}")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataItemId").value(id))
                .andExpect(jsonPath("$.data.measurementThreshold.cautionValue").value(70.5))
                .andExpect(jsonPath("$.data.measurementThreshold.direction").value("ABOVE"))
                .andExpect(jsonPath("$.data.stateAlarmCondition").doesNotExist())
                .andExpect(jsonPath("$.data.appliedAt").exists());
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM data_item WHERE data_item_id = ?", id);
        assertThat(((Number) row.get("caution_limit")).doubleValue()).isEqualTo(70.5);
        assertThat(((Number) row.get("critical_limit")).doubleValue()).isEqualTo(85.0);
        String actor = "|" + user.getId();
        assertThat(changes()).containsExactly(
                "THRESHOLD|OR-01-1/temp|주의 기준|null|70.5" + actor,
                "THRESHOLD|OR-01-1/temp|경보 기준|null|85" + actor,
                "THRESHOLD|OR-01-1/temp|기준 방향|null|ABOVE" + actor);

        mvc.perform(json(put(MASTER + "/data-items/" + id + "/threshold"),
                        "{\"cautionValue\":90,\"criticalValue\":85,\"direction\":\"ABOVE\"}")
                        .header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_THRESHOLD_ORDER"));
        assertThat(changes()).hasSize(3);
    }

    @Test
    void thresholdUpdateChangesStateAlarmAndRejectsCumulative() throws Exception {
        long door = item(equipmentId, "door", "STATE", 1);
        mvc.perform(json(put(MASTER + "/data-items/" + door + "/threshold"), "{\"alarmValue\":0,\"grade\":\"CRITICAL\"}")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stateAlarmCondition.alarmValue").value(0))
                .andExpect(jsonPath("$.data.stateAlarmCondition.grade").value("CRITICAL"))
                .andExpect(jsonPath("$.data.measurementThreshold").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT alarm_state_grade FROM data_item WHERE data_item_id = ?",
                String.class, door)).isEqualTo("CRITICAL");

        long counter = item(equipmentId, "prod_count", "CUMULATIVE", 2);
        mvc.perform(json(put(MASTER + "/data-items/" + counter + "/threshold"), "{\"alarmValue\":1,\"grade\":\"CAUTION\"}")
                        .header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("THRESHOLD_NOT_SUPPORTED"));
    }

    @Test
    void thresholdUpdateValidatesShapeAndItem() throws Exception {
        long temp = item(equipmentId, "temp", "MEASUREMENT", 1);
        long door = item(equipmentId, "door", "STATE", 2);
        Object[][] invalid = {
            {temp, "{}"},
            {temp, "{\"cautionValue\":1,\"criticalValue\":2,\"direction\":\"ABOVE\",\"alarmValue\":1,\"grade\":\"CAUTION\"}"},
            {temp, "{\"cautionValue\":1,\"criticalValue\":2}"},
            {temp, "{\"alarmValue\":1,\"grade\":\"CAUTION\"}"},
            {door, "{\"cautionValue\":1,\"criticalValue\":2,\"direction\":\"ABOVE\"}"},
            {door, "{\"alarmValue\":1}"},
            {door, "{\"alarmValue\":3,\"grade\":\"CAUTION\"}"},
        };
        for (Object[] c : invalid) {
            mvc.perform(json(put(MASTER + "/data-items/" + c[0] + "/threshold"), (String) c[1])
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(json(put(MASTER + "/data-items/999999/threshold"), "{\"alarmValue\":1,\"grade\":\"CAUTION\"}")
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DATA_ITEM_NOT_FOUND"));
        assertThat(changes()).isEmpty();
    }

    @Test
    void adminMayAlsoWrite() throws Exception {
        String admin = bearer(approved("itemadmin", Permission.ADMIN));
        mvc.perform(json(post(MASTER + "/equipment/" + equipmentId + "/data-items"), measurementBody("flow", null))
                        .header("Authorization", admin))
                .andExpect(status().isCreated());
    }

    // ---------- helpers ----------

    private static String measurementBody(String code, String threshold) {
        return "{" + (code == null ? "" : "\"dataItemCode\":\"" + code + "\",")
                + "\"dataItemName\":\"냉각수 유량\",\"valueType\":\"MEASUREMENT\",\"unit\":\"L/min\","
                + "\"collectionIntervalSeconds\":3"
                + (threshold == null ? "" : ",\"measurementThreshold\":" + threshold) + "}";
    }

    private AccountEntity approved(String loginId, Permission permission) {
        LocalDateTime now = now();
        AccountEntity account = AccountEntity.signup(loginId, passwords.encode("DataItem123!"), loginId, now);
        account.approve(permission.name(), null, now);
        return accounts.save(account);
    }

    private String bearer(AccountEntity account) {
        return "Bearer " + jwt.issue(account.getId(), Permission.valueOf(account.getRole()), clock.instant()).value();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(java.time.ZoneId.of("Asia/Seoul")));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long equipment(String code, long processId) {
        return insert("INSERT INTO equipment(equip_code, name, equip_type, process_id, comm_type)"
                + " VALUES (?, '시험 설비', '시험기', ?, 'MODBUS_TCP')", code, processId);
    }

    private long item(long equipment, String code, String kind, int sortOrder) {
        return insert("INSERT INTO data_item(equipment_id, item_code, name, item_kind, sort_order, decimal_places)"
                + " VALUES (?, ?, ?, ?, ?, ?)", equipment, code, code + " 이름", kind, sortOrder,
                "MEASUREMENT".equals(kind) ? 1 : 0);
    }

    private void measurement(long dataItemId, LocalDateTime at, double value) {
        jdbc.update("INSERT INTO measurement(data_item_id, measured_at, measured_value) VALUES (?, ?, ?)",
                dataItemId, at, value);
    }

    private void latest(long dataItemId, double value, LocalDateTime at) {
        jdbc.update("INSERT INTO measurement_latest(data_item_id, measured_value, measured_at, received_at)"
                + " VALUES (?, ?, ?, ?)", dataItemId, value, at, at);
    }

    private void alarm(long dataItemId, LocalDateTime at, String state) {
        long equipment = jdbc.queryForObject("SELECT equipment_id FROM data_item WHERE data_item_id = ?",
                Long.class, dataItemId);
        insert("INSERT INTO alarm(equipment_id, data_item_id, grade, message, trigger_value, limit_value,"
                        + " process_state, occurred_at) VALUES (?, ?, 'CAUTION', '시험 알람', 1, 2, ?, ?)",
                equipment, dataItemId, state, at);
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

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    /** target_type|target_name|field_name|old_value|new_value|changed_by, 기록한 순서대로. */
    private List<String> changes() {
        return jdbc.query("SELECT * FROM change_log ORDER BY change_id", (rs, row) -> String.join("|",
                rs.getString("target_type"), rs.getString("target_name"), rs.getString("field_name"),
                String.valueOf(rs.getString("old_value")), String.valueOf(rs.getString("new_value")),
                String.valueOf(rs.getLong("changed_by"))));
    }
}

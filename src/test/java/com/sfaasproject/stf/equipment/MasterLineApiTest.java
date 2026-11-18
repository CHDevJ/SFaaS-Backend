package com.sfaasproject.stf.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * MST-005~009 라인과 설비 배치. 실제 V1 과 같은 컬럼을 가진 B 의 H2 스키마를 써서
 * 설비 상태(equipment_state)와 발생 중 알람(alarm)까지 함께 확인한다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:master-lines;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import(AuthTestConfig.class)
class MasterLineApiTest {

    private static final String BASE = "/api/v1/master/lines";

    @MockitoBean RedisTelegramLinkTokenStore telegramLinkTokenStore;
    @MockitoBean TemporaryPasswordSender temporaryPasswordSender;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;

    AccountEntity manager;
    String token;

    @BeforeEach
    void setUp() {
        for (String table : List.of("alarm", "equipment_state", "data_item", "equipment", "process", "line",
                "change_log", "auth_token", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        manager = approved("lineuser", Permission.USER);
        token = bearer(manager);
    }

    // ---------- MST-005 ----------

    @Test
    void listShowsActiveLinesInDisplayOrderWithEquipmentInProcessOrder() throws Exception {
        long second = line("내륜", 2);
        long first = line("외륜", 1);
        long deletedLine = line("폐기", 3);
        jdbc.update("UPDATE line SET deleted_at = ? WHERE line_id = ?", LocalDateTime.now(clock), deletedLine);
        long forging = process(first, 1, "단조");
        long turning = process(first, 2, "선삭");
        long turningEq = equipment("OR-02-1", "외륜 선삭기 1호기", turning);
        long forgingEq = equipment("OR-01-1", "외륜 단조기 1호기", forging);
        long removed = equipment("OR-01-9", "삭제된 설비", forging);
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", LocalDateTime.now(clock), removed);
        LocalDateTime since = LocalDateTime.of(2026, 11, 17, 9, 30);
        jdbc.update("INSERT INTO equipment_state(equipment_id, status, status_since) VALUES (?, 'RUNNING', ?)",
                forgingEq, since);
        long item = dataItem(forgingEq, "coolant_flow");
        long other = dataItem(forgingEq, "spindle_temp");
        alarm(forgingEq, item, "ACTIVE");
        alarm(forgingEq, other, "ACTIVE");
        alarm(forgingEq, item, "CLOSED");

        mvc.perform(get(BASE).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].lineId").value(first))
                .andExpect(jsonPath("$.data[0].lineName").value("외륜"))
                .andExpect(jsonPath("$.data[0].displayOrder").value(1))
                .andExpect(jsonPath("$.data[0].lineType").value("PART"))
                .andExpect(jsonPath("$.data[0].equipment.length()").value(2))
                .andExpect(jsonPath("$.data[0].equipment[0].lineType").value("PART"))
                .andExpect(jsonPath("$.data[0].equipment[0].equipmentId").value(forgingEq))
                .andExpect(jsonPath("$.data[0].equipment[0].equipmentCode").value("OR-01-1"))
                .andExpect(jsonPath("$.data[0].equipment[0].lineName").value("외륜"))
                .andExpect(jsonPath("$.data[0].equipment[0].processId").value(forging))
                .andExpect(jsonPath("$.data[0].equipment[0].processName").value("단조"))
                .andExpect(jsonPath("$.data[0].equipment[0].processOrder").value(1))
                .andExpect(jsonPath("$.data[0].equipment[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].equipment[0].statusStartedAt").value("2026-11-17T09:30:00+09:00"))
                .andExpect(jsonPath("$.data[0].equipment[0].activeAlarmCount").value(2))
                .andExpect(jsonPath("$.data[0].equipment[1].equipmentId").value(turningEq))
                .andExpect(jsonPath("$.data[0].equipment[1].status").value("COMMUNICATION_LOST"))
                .andExpect(jsonPath("$.data[0].equipment[1].activeAlarmCount").value(0))
                .andExpect(jsonPath("$.data[1].lineId").value(second))
                .andExpect(jsonPath("$.data[1].equipment.length()").value(0));
    }

    @Test
    void everyEndpointRequiresLogin() throws Exception {
        long id = line("외륜", 1);
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(json(post(BASE), lineBody("새 라인", 1))).andExpect(status().isUnauthorized());
        mvc.perform(json(put(BASE + "/" + id), lineBody("새 라인", 1))).andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(json(patch(BASE + "/" + id + "/layout"), "{\"equipment\":[{\"equipmentId\":1,\"processOrder\":1}]}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- MST-006 ----------

    @Test
    void createStoresPartLineAndRecordsChange() throws Exception {
        mvc.perform(json(post(BASE), lineBody("  샤프트  ", 4)).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lineName").value("샤프트"))
                .andExpect(jsonPath("$.data.displayOrder").value(4))
                .andExpect(jsonPath("$.data.lineType").value("PART"))
                .andExpect(jsonPath("$.data.equipment.length()").value(0));

        Map<String, Object> row = jdbc.queryForMap("SELECT name, line_type, sort_order, deleted_at FROM line");
        assertThat(row.get("name")).isEqualTo("샤프트");
        assertThat(row.get("line_type")).isEqualTo("PART");
        assertThat(((Number) row.get("sort_order")).intValue()).isEqualTo(4);
        assertThat(row.get("deleted_at")).isNull();
        assertThat(changes()).containsExactly("LINE|샤프트|라인 등록|null|샤프트|" + manager.getId());
    }

    @Test
    void createStoresGivenLineType() throws Exception {
        mvc.perform(json(post(BASE), "{\"lineName\":\"조립\",\"displayOrder\":5,\"lineType\":\"ASSEMBLY\"}")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lineType").value("ASSEMBLY"));
        assertThat(jdbc.queryForObject("SELECT line_type FROM line", String.class)).isEqualTo("ASSEMBLY");
    }

    @Test
    void adminAndUserMayWriteMasterData() throws Exception {
        String admin = bearer(approved("sysadmin", Permission.ADMIN));
        mvc.perform(json(post(BASE), lineBody("사용자 라인", 1)).header("Authorization", token))
                .andExpect(status().isCreated());
        mvc.perform(json(post(BASE), lineBody("관리 라인", 2)).header("Authorization", admin))
                .andExpect(status().isCreated());
    }

    @Test
    void createRejectsNameAlreadyUsedEvenByDeletedLine() throws Exception {
        line("외륜", 1);
        long deleted = line("케이지", 2);
        jdbc.update("UPDATE line SET deleted_at = ? WHERE line_id = ?", LocalDateTime.now(clock), deleted);

        for (String name : List.of("외륜", "케이지")) {
            mvc.perform(json(post(BASE), lineBody(name, 9)).header("Authorization", token))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("LINE_NAME_DUPLICATE"));
        }
        assertThat(count("line")).isEqualTo(2);
        assertThat(changes()).isEmpty();
    }

    @Test
    void createValidatesNameLengthAndDisplayOrder() throws Exception {
        String fifty = "가".repeat(50);
        for (String body : List.of(
                lineBody("", 1), lineBody("   ", 1), lineBody(fifty + "나", 1), lineBody("라인", 0),
                "{\"lineName\":\"라인\",\"displayOrder\":1,\"lineType\":\"OTHER\"}",
                "{\"lineName\":\"라인\"}", "{\"displayOrder\":1}", "{\"lineName\":\"라인\",\"displayOrder\":\"x\"}",
                "not-json")) {
            mvc.perform(json(post(BASE), body).header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(count("line")).isZero();

        mvc.perform(json(post(BASE), lineBody(fifty, 1)).header("Authorization", token))
                .andExpect(status().isCreated());
    }

    // ---------- MST-007 ----------

    @Test
    void updateChangesNameAndOrderAndRecordsOnlyChangedFields() throws Exception {
        long id = line("외륜", 1);

        mvc.perform(json(put(BASE + "/" + id), lineBody("외륜 A", 1)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineId").value(id))
                .andExpect(jsonPath("$.data.lineName").value("외륜 A"))
                .andExpect(jsonPath("$.data.displayOrder").value(1));
        assertThat(changes()).containsExactly("LINE|외륜|이름|외륜|외륜 A|" + manager.getId());

        mvc.perform(json(put(BASE + "/" + id), lineBody("외륜 A", 3)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayOrder").value(3));
        assertThat(changes()).hasSize(2).last().isEqualTo("LINE|외륜 A|표시순서|1|3|" + manager.getId());

        mvc.perform(json(put(BASE + "/" + id), lineBody("외륜 A", 3)).header("Authorization", token))
                .andExpect(status().isOk());
        assertThat(changes()).hasSize(2);
    }

    @Test
    void updateRejectsNameOfAnotherLineButAcceptsItsOwnName() throws Exception {
        long id = line("외륜", 1);
        line("내륜", 2);

        mvc.perform(json(put(BASE + "/" + id), lineBody("내륜", 1)).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LINE_NAME_DUPLICATE"));
        assertThat(jdbc.queryForObject("SELECT name FROM line WHERE line_id = ?", String.class, id)).isEqualTo("외륜");

        mvc.perform(json(put(BASE + "/" + id), lineBody("외륜", 5)).header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void updateReturnsNotFoundForMissingOrDeletedLine() throws Exception {
        long deleted = line("외륜", 1);
        jdbc.update("UPDATE line SET deleted_at = ? WHERE line_id = ?", LocalDateTime.now(clock), deleted);
        for (long id : new long[] {deleted, 999_999L}) {
            mvc.perform(json(put(BASE + "/" + id), lineBody("새 이름", 1)).header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("LINE_NOT_FOUND"));
        }
        mvc.perform(json(put(BASE + "/" + deleted), lineBody("", 1)).header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateKeepsLineTypeWhenOmittedAndRecordsTypeChange() throws Exception {
        long id = insert("INSERT INTO line(name, line_type, sort_order) VALUES ('조립', 'ASSEMBLY', 1)");

        mvc.perform(json(put(BASE + "/" + id), lineBody("조립", 2)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineType").value("ASSEMBLY"));

        mvc.perform(json(put(BASE + "/" + id), "{\"lineName\":\"조립\",\"displayOrder\":2,\"lineType\":\"PART\"}")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineType").value("PART"));
        assertThat(changes()).hasSize(2).last().isEqualTo("LINE|조립|라인 종류|ASSEMBLY|PART|" + manager.getId());
    }

    // ---------- MST-008 ----------

    @Test
    void deleteMarksEmptyLineDeletedAndHidesIt() throws Exception {
        long id = line("외륜", 1);
        process(id, 1, "단조");
        long other = line("내륜", 2);
        long oldProcess = process(other, 1, "단조");
        long gone = equipment("IR-01-1", "삭제된 설비", oldProcess);
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", LocalDateTime.now(clock), gone);

        mvc.perform(delete(BASE + "/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(delete(BASE + "/" + other).header("Authorization", token)).andExpect(status().isNoContent());

        assertThat(count("line WHERE deleted_at IS NOT NULL")).isEqualTo(2);
        assertThat(count("process")).isEqualTo(2);
        assertThat(changes()).containsExactly(
                "LINE|외륜|라인 삭제|사용|삭제|" + manager.getId(),
                "LINE|내륜|라인 삭제|사용|삭제|" + manager.getId());
        mvc.perform(get(BASE).header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete(BASE + "/" + id).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LINE_NOT_FOUND"));
    }

    @Test
    void deleteRejectsLineThatStillHasEquipment() throws Exception {
        long id = line("외륜", 1);
        equipment("OR-01-1", "외륜 단조기 1호기", process(id, 1, "단조"));

        mvc.perform(delete(BASE + "/" + id).header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LINE_HAS_EQUIPMENT"));
        assertThat(count("line WHERE deleted_at IS NULL")).isEqualTo(1);
        assertThat(changes()).isEmpty();
    }

    // ---------- MST-009 ----------

    @Test
    void layoutMovesEquipmentBetweenProcessesOfTheSameLine() throws Exception {
        long id = line("외륜", 1);
        long forging = process(id, 1, "단조");
        long turning = process(id, 2, "선삭");
        long a = equipment("OR-01-1", "단조기", forging);
        long b = equipment("OR-01-2", "선삭기", forging);

        mvc.perform(json(patch(BASE + "/" + id + "/layout"), layout(a, 1, b, 2)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineId").value(id))
                .andExpect(jsonPath("$.data.equipment.length()").value(2))
                .andExpect(jsonPath("$.data.equipment[0].equipmentId").value(a))
                .andExpect(jsonPath("$.data.equipment[0].processOrder").value(1))
                .andExpect(jsonPath("$.data.equipment[1].equipmentId").value(b))
                .andExpect(jsonPath("$.data.equipment[1].processId").value(turning))
                .andExpect(jsonPath("$.data.equipment[1].processName").value("선삭"))
                .andExpect(jsonPath("$.data.equipment[1].processOrder").value(2));

        assertThat(processOf(a)).isEqualTo(forging);
        assertThat(processOf(b)).isEqualTo(turning);
        assertThat(changes()).containsExactly("EQUIPMENT|OR-01-2|공정 배치|1. 단조|2. 선삭|" + manager.getId());
    }

    @Test
    void layoutRejectsEquipmentOfAnotherLineWithoutChangingAnything() throws Exception {
        long id = line("외륜", 1);
        long forging = process(id, 1, "단조");
        process(id, 2, "선삭");
        long other = line("내륜", 2);
        long mine = equipment("OR-01-1", "단조기", forging);
        long foreign = equipment("IR-01-1", "내륜 단조기", process(other, 1, "단조"));

        mvc.perform(json(patch(BASE + "/" + id + "/layout"), layout(mine, 2, foreign, 1)).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(processOf(mine)).isEqualTo(forging);
        assertThat(changes()).isEmpty();
    }

    @Test
    void layoutRejectsProcessOrderThatTheLineDoesNotHave() throws Exception {
        long id = line("외륜", 1);
        long forging = process(id, 1, "단조");
        long turning = process(id, 2, "선삭");
        long a = equipment("OR-01-1", "단조기", forging);
        long b = equipment("OR-01-2", "선삭기", forging);

        mvc.perform(json(patch(BASE + "/" + id + "/layout"), layout(a, 2, b, 7)).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(processOf(a)).isEqualTo(forging);
        assertThat(processOf(b)).isEqualTo(forging);
        assertThat(turning).isNotEqualTo(forging);
        assertThat(changes()).isEmpty();
    }

    @Test
    void layoutRejectsUnknownOrDeletedEquipment() throws Exception {
        long id = line("외륜", 1);
        long forging = process(id, 1, "단조");
        long gone = equipment("OR-01-9", "삭제된 설비", forging);
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", LocalDateTime.now(clock), gone);

        for (long equipmentId : new long[] {gone, 999_999L}) {
            mvc.perform(json(patch(BASE + "/" + id + "/layout"),
                            "{\"equipment\":[{\"equipmentId\":" + equipmentId + ",\"processOrder\":1}]}")
                            .header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("EQUIPMENT_NOT_FOUND"));
        }
    }

    @Test
    void layoutValidatesRequestAndLine() throws Exception {
        long id = line("외륜", 1);
        long a = equipment("OR-01-1", "단조기", process(id, 1, "단조"));
        for (String body : List.of("{\"equipment\":[]}", "{}", layout(a, 1, a, 1),
                "{\"equipment\":[{\"equipmentId\":" + a + ",\"processOrder\":0}]}",
                "{\"equipment\":[{\"equipmentId\":" + a + "}]}", "{\"equipment\":[null]}")) {
            mvc.perform(json(patch(BASE + "/" + id + "/layout"), body).header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(json(patch(BASE + "/999999/layout"), layout(a, 1)).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LINE_NOT_FOUND"));
    }

    @Test
    void layoutWithoutRealChangeRecordsNothing() throws Exception {
        long id = line("외륜", 1);
        long a = equipment("OR-01-1", "단조기", process(id, 1, "단조"));
        mvc.perform(json(patch(BASE + "/" + id + "/layout"), layout(a, 1)).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipment[0].processOrder").value(1));
        assertThat(changes()).isEmpty();
    }

    // ---------- helpers ----------

    private AccountEntity approved(String loginId, Permission permission) {
        LocalDateTime now = LocalDateTime.now(clock);
        AccountEntity account = AccountEntity.signup(loginId, passwords.encode("MasterLine123!"), loginId, now);
        account.approve(permission.name(), null, now);
        return accounts.save(account);
    }

    private String bearer(AccountEntity account) {
        return "Bearer " + jwt.issue(account.getId(), Permission.valueOf(account.getRole()), clock.instant()).value();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String lineBody(String name, int order) {
        return "{\"lineName\":\"" + name + "\",\"displayOrder\":" + order + "}";
    }

    private static String layout(long... pairs) {
        StringBuilder body = new StringBuilder("{\"equipment\":[");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"equipmentId\":").append(pairs[i]).append(",\"processOrder\":").append(pairs[i + 1])
                    .append('}');
        }
        return body.append("]}").toString();
    }

    private long line(String name, int order) {
        return insert("INSERT INTO line(name, line_type, sort_order) VALUES (?, 'PART', ?)", name, order);
    }

    private long process(long lineId, int seq, String name) {
        return insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, ?, ?)", lineId, seq, name);
    }

    private long equipment(String code, String name, long processId) {
        return insert("INSERT INTO equipment(equip_code, name, equip_type, process_id, comm_type)"
                + " VALUES (?, ?, '시험', ?, 'MODBUS_TCP')", code, name, processId);
    }

    private long dataItem(long equipmentId, String code) {
        return insert("INSERT INTO data_item(equipment_id, item_code, name) VALUES (?, ?, ?)",
                equipmentId, code, code);
    }

    private void alarm(long equipmentId, long dataItemId, String state) {
        insert("INSERT INTO alarm(equipment_id, data_item_id, grade, message, trigger_value, limit_value,"
                        + " process_state, occurred_at) VALUES (?, ?, 'CAUTION', '시험 알람', 1, 2, ?, ?)",
                equipmentId, dataItemId, state, LocalDateTime.now(clock));
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
        return keys.getKeyList().isEmpty() ? 0 : ((Number) keys.getKeyList().get(0).values().iterator().next())
                .longValue();
    }

    private long processOf(long equipmentId) {
        return jdbc.queryForObject("SELECT process_id FROM equipment WHERE equipment_id = ?", Long.class, equipmentId);
    }

    private int count(String fromClause) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Integer.class);
    }

    /** target_type|target_name|field_name|old_value|new_value|changed_by, 기록한 순서대로. */
    private List<String> changes() {
        return jdbc.query("SELECT * FROM change_log ORDER BY change_id", (rs, row) -> String.join("|",
                rs.getString("target_type"), rs.getString("target_name"), rs.getString("field_name"),
                String.valueOf(rs.getString("old_value")), String.valueOf(rs.getString("new_value")),
                String.valueOf(rs.getLong("changed_by"))));
    }
}

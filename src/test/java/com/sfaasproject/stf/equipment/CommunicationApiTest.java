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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * COM-001/002 통신 현황. "오늘" 집계를 확인하려고 시계를 2026-11-18 12:00(KST)에 고정한다.
 * B 의 H2 스키마(V1 과 같은 컬럼)에 설비 상태와 상태 이력을 넣는다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:communications;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import({AuthTestConfig.class, CommunicationApiTest.FixedClock.class})
class CommunicationApiTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);
    static final LocalDateTime TODAY = NOW.toLocalDate().atStartOfDay();

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }

    @MockitoBean RedisTelegramLinkTokenStore telegramLinkTokenStore;
    @MockitoBean TemporaryPasswordSender temporaryPasswordSender;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;

    String token;
    long outer;
    long inner;
    long connected;
    long flaky;
    long lost;
    long never;

    @BeforeEach
    void setUp() {
        for (String table : List.of("alarm_action", "alarm", "measurement", "measurement_latest", "data_item",
                "equipment_status_history", "equipment_state", "part", "equipment", "process", "stock_history", "line",
                "change_log", "auth_token", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        AccountEntity account = AccountEntity.signup("comm", passwords.encode("Comm12345!"), "comm", NOW);
        account.approve(Permission.USER.name(), null, NOW);
        account = accounts.save(account);
        token = "Bearer " + jwt.issue(account.getId(), Permission.USER, clock.instant()).value();

        outer = insert("INSERT INTO line(name, line_type, sort_order) VALUES ('외륜', 'PART', 1)");
        inner = insert("INSERT INTO line(name, line_type, sort_order) VALUES ('내륜', 'PART', 2)");
        long outerProcess = insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, 1, '단조')", outer);
        long innerProcess = insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, 1, '단조')", inner);
        connected = equipment("OR-01-1", outerProcess);
        flaky = equipment("OR-01-2", outerProcess);
        lost = equipment("OR-01-3", outerProcess);
        never = equipment("IR-01-1", innerProcess);
        long gone = equipment("OR-01-9", outerProcess);
        jdbc.update("UPDATE equipment SET deleted_at = ? WHERE equipment_id = ?", NOW, gone);
        state(gone, "COMMUNICATION_LOST", TODAY);

        state(connected, "RUNNING", TODAY.minusDays(1));
        state(flaky, "IDLE", NOW.minusMinutes(30));
        state(lost, "COMMUNICATION_LOST", NOW.minusMinutes(20));
        // flaky: 어제부터 이어진 끊김(오늘 0:00~0:10, 10분) + 오늘 두 번(5분, 7분 30초) = 3회 22분.
        history(flaky, TODAY.minusHours(1), TODAY.plusMinutes(10));
        history(flaky, TODAY.plusHours(8), TODAY.plusHours(8).plusMinutes(5));
        history(flaky, TODAY.plusHours(9), TODAY.plusHours(9).plusMinutes(7).plusSeconds(30));
        history(flaky, TODAY.minusDays(1), TODAY.minusDays(1).plusHours(1));
        // lost: 20분 전부터 지금까지 끊김 = 1회 20분.
        history(lost, NOW.minusMinutes(20), null);
    }

    @Test
    void listSortsByTodayDisconnectionsAndCountsSummary() throws Exception {
        mvc.perform(get("/api/v1/communications").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.status").value("PARTIAL_DISCONNECTED"))
                .andExpect(jsonPath("$.data.summary.totalEquipmentCount").value(4))
                .andExpect(jsonPath("$.data.summary.disconnectedEquipmentCount").value(2))
                .andExpect(jsonPath("$.data.summary.allDisconnectedSince").doesNotExist())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(flaky))
                .andExpect(jsonPath("$.data.items[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.items[0].todayDisconnectionCount").value(3))
                .andExpect(jsonPath("$.data.items[0].todayDisconnectionMinutes").value(22))
                .andExpect(jsonPath("$.data.items[0].disconnectedSince").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].equipmentId").value(lost))
                .andExpect(jsonPath("$.data.items[1].status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.items[1].todayDisconnectionCount").value(1))
                .andExpect(jsonPath("$.data.items[1].todayDisconnectionMinutes").value(20))
                .andExpect(jsonPath("$.data.items[1].disconnectedSince").value("2026-11-18T11:40:00+09:00"))
                .andExpect(jsonPath("$.data.items[1].equipmentCode").value("OR-01-3"))
                .andExpect(jsonPath("$.data.items[1].lineName").value("외륜"))
                .andExpect(jsonPath("$.data.items[1].communicationMethod").value("MQTT"))
                .andExpect(jsonPath("$.data.items[2].equipmentId").value(connected))
                .andExpect(jsonPath("$.data.items[2].todayDisconnectionCount").value(0))
                .andExpect(jsonPath("$.data.items[2].lastReceivedAt").value("2026-11-18T11:59:57+09:00"))
                // 값을 받은 적 없는 설비는 끊김으로 본다.
                .andExpect(jsonPath("$.data.items[3].equipmentId").value(never))
                .andExpect(jsonPath("$.data.items[3].status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.items[3].lastReceivedAt").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(4));
    }

    @Test
    void listFiltersByLineAndStatusAndPages() throws Exception {
        mvc.perform(get("/api/v1/communications").param("lineId", String.valueOf(inner))
                        .header("Authorization", token))
                .andExpect(jsonPath("$.data.summary.status").value("ALL_DISCONNECTED"))
                .andExpect(jsonPath("$.data.summary.totalEquipmentCount").value(1))
                .andExpect(jsonPath("$.data.summary.allDisconnectedSince").exists())
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(never));
        mvc.perform(get("/api/v1/communications").param("status", "DISCONNECTED").header("Authorization", token))
                .andExpect(jsonPath("$.data.summary.totalEquipmentCount").value(4))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(lost))
                .andExpect(jsonPath("$.data.items[1].equipmentId").value(never));
        mvc.perform(get("/api/v1/communications").param("size", "1").param("page", "1")
                        .header("Authorization", token))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(lost))
                .andExpect(jsonPath("$.meta.totalPages").value(4));
        mvc.perform(get("/api/v1/communications").param("lineId", "999999").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("ALL_NORMAL"))
                .andExpect(jsonPath("$.data.summary.totalEquipmentCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        for (String[] param : new String[][] {{"status", "LOST"}, {"size", "101"}, {"page", "-1"}, {"lineId", "x"}}) {
            mvc.perform(get("/api/v1/communications").param(param[0], param[1]).header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void listFiltersByProcessAndSummarizesThatProcess() throws Exception {
        long forging = jdbc.queryForObject("SELECT process_id FROM process WHERE line_id = ? AND process_seq = 1",
                Long.class, outer);
        long turning = insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, 2, '선삭')", outer);
        long turner = equipment("OR-02-1", turning);
        state(turner, "RUNNING", TODAY);

        // 단조 공정의 설비 셋(연결 둘, 끊김 하나)만 나오고 집계도 그 공정만 센다.
        mvc.perform(get("/api/v1/communications").param("processId", String.valueOf(forging))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("PARTIAL_DISCONNECTED"))
                .andExpect(jsonPath("$.data.summary.totalEquipmentCount").value(3))
                .andExpect(jsonPath("$.data.summary.disconnectedEquipmentCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(flaky));
        mvc.perform(get("/api/v1/communications").param("lineId", String.valueOf(outer))
                        .param("processId", String.valueOf(turning)).param("status", "CONNECTED")
                        .header("Authorization", token))
                .andExpect(jsonPath("$.data.summary.status").value("ALL_NORMAL"))
                .andExpect(jsonPath("$.data.summary.totalEquipmentCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(turner));
        // 다른 라인의 공정은 0대다.
        mvc.perform(get("/api/v1/communications").param("lineId", String.valueOf(inner))
                        .param("processId", String.valueOf(forging)).header("Authorization", token))
                .andExpect(jsonPath("$.data.summary.totalEquipmentCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/v1/communications").param("processId", "x").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void summaryCoversAllActiveEquipmentAndNeedsLogin() throws Exception {
        mvc.perform(get("/api/v1/communications/summary").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL_DISCONNECTED"))
                .andExpect(jsonPath("$.data.totalEquipmentCount").value(4))
                .andExpect(jsonPath("$.data.disconnectedEquipmentCount").value(2));

        jdbc.update("UPDATE equipment_state SET status = 'RUNNING' WHERE equipment_id = ?", lost);
        state(never, "RUNNING", NOW);
        mvc.perform(get("/api/v1/communications/summary").header("Authorization", token))
                .andExpect(jsonPath("$.data.status").value("ALL_NORMAL"))
                .andExpect(jsonPath("$.data.disconnectedEquipmentCount").value(0));

        jdbc.update("UPDATE equipment_state SET status = 'COMMUNICATION_LOST', status_since = ?", NOW.minusMinutes(3));
        mvc.perform(get("/api/v1/communications/summary").header("Authorization", token))
                .andExpect(jsonPath("$.data.status").value("ALL_DISCONNECTED"))
                .andExpect(jsonPath("$.data.allDisconnectedSince").value("2026-11-18T11:57:00+09:00"));

        mvc.perform(get("/api/v1/communications/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/communications")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long equipment(String code, long processId) {
        return insert("INSERT INTO equipment(equip_code, name, equip_type, process_id, comm_type, created_at)"
                + " VALUES (?, ?, '시험기', ?, 'MQTT', ?)", code, code + " 설비", processId, TODAY.minusDays(10));
    }

    private void state(long equipmentId, String status, LocalDateTime since) {
        jdbc.update("INSERT INTO equipment_state(equipment_id, status, status_since, last_received_at)"
                + " VALUES (?, ?, ?, ?)", equipmentId, status, since, NOW.minusSeconds(3));
    }

    private void history(long equipmentId, LocalDateTime started, LocalDateTime ended) {
        jdbc.update("INSERT INTO equipment_status_history(equipment_id, status, started_at, ended_at)"
                + " VALUES (?, 'COMMUNICATION_LOST', ?, ?)", equipmentId, started, ended);
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

package com.sfaasproject.stf.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.service.AccessRevocationStore;
import com.sfaasproject.stf.measurement.service.IngestionMessageGuard;
import com.sfaasproject.stf.operations.port.BPorts;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * INT-001 누적 카운터 리셋. 초기화 시각(00:00) 밖에서 카운터가 줄어도(PLC·Node-RED 재시작) 메시지를 거절하지 않고,
 * 같은 묶음의 가동 신호·센서 값을 저장하며, 줄어든 값은 생산으로 더하지 않는다(API_SPEC 4장).
 * 2026-11-18 통합 점검에서 이 경우 400 으로 메시지 전체가 버려져 설비가 통신 끊김으로 보이던 문제를 막는다.
 */
@SpringBootTest(properties = {
    "stf.backend-b.enabled=true",
    "stf.backend-b.jobs-enabled=false",
    "stf.backend-b.ingestion-enabled=true",
    "stf.telegram.webhook-secret=counter-test-callback",
    "INTERNAL_API_KEY=counter-test-internal-key-0123456789",
    "spring.datasource.url=jdbc:h2:mem:counter-reset;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import(IngestionCounterResetTest.FixedClock.class)
class IngestionCounterResetTest {

    static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 18, 12, 0);
    static final String KEY = "counter-test-internal-key-0123456789";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOON.plusMinutes(1).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                    ZoneId.of("Asia/Seoul"));
        }
    }

    @MockitoBean AccessRevocationStore revocations;
    @MockitoBean TelegramDeliveryService delivery;
    @MockitoBean BPorts.LinkTokens tokens;
    @MockitoBean IngestionMessageGuard guard;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        when(guard.acquire(anyString())).thenAnswer(a -> seen.add(a.getArgument(0))
                ? IngestionMessageGuard.Acquisition.ACQUIRED : IngestionMessageGuard.Acquisition.COMPLETED);
        for (String table : List.of("backend_event_outbox", "production_log", "measurement", "measurement_latest",
                "alarm", "data_item", "equipment_status_history", "equipment_state", "equipment", "process",
                "stock_history", "line")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO line(line_id, name, line_type, sort_order) VALUES (1, '외륜', 'PART', 1)");
        jdbc.update("INSERT INTO process(process_id, line_id, process_seq, name) VALUES (1, 1, 1, '연삭')");
        jdbc.update("INSERT INTO equipment(equipment_id, equip_code, name, equip_type, process_id, comm_type)"
                + " VALUES (1, 'OR-04-1', '연삭기', '연삭기', 1, 'MQTT')");
        jdbc.update("INSERT INTO data_item(data_item_id, equipment_id, item_code, name, item_kind, item_role,"
                + " decimal_places, sort_order) VALUES (1, 1, 'run_signal', '가동 신호', 'STATE', 'RUN_SIGNAL', 0, 1)");
        jdbc.update("INSERT INTO data_item(data_item_id, equipment_id, item_code, name, item_kind, item_role,"
                + " decimal_places, reset_time, sort_order)"
                + " VALUES (2, 1, 'prod_count', '생산 수', 'CUMULATIVE', 'PRODUCTION', 0, '00:00:00', 2)");
        jdbc.update("INSERT INTO data_item(data_item_id, equipment_id, item_code, name, item_kind, item_role,"
                + " decimal_places, sort_order) VALUES (3, 1, 'coolant_temp', '냉각수 온도', 'MEASUREMENT', 'NONE', 1, 3)");
    }

    @Test
    void midDayCounterResetIsAcceptedAndNotCountedAsProduction() throws Exception {
        ingest("m1", NOON, 50, 20.0);
        // 12:00:03 에 카운터가 50 -> 3 으로 줄었다(초기화 시각 00:00 밖). 예전에는 400 으로 메시지 전체가 버려졌다.
        ingest("m2", NOON.plusSeconds(3), 3, 21.5);

        assertThat(latest(2)).isEqualTo(3.0);
        assertThat(latest(3)).as("같은 묶음의 센서 값도 저장된다").isEqualTo(21.5);
        assertThat(latest(1)).isEqualTo(1.0);
        assertThat(jdbc.queryForObject("SELECT counter_status FROM measurement_latest WHERE data_item_id = 2",
                String.class)).isEqualTo("COUNTER_RESET_PENDING");
        assertThat(produced()).as("리셋은 생산으로 더하지 않는다").isZero();

        // 리셋 뒤에는 새 기준점(3)에서 늘어난 만큼만 더한다.
        ingest("m3", NOON.plusSeconds(6), 5, 21.0);
        assertThat(produced()).isEqualTo(2);
    }

    private void ingest(String id, LocalDateTime at, int counter, double temp) throws Exception {
        String body = """
                {"messageId":"%s","equipmentCode":"OR-04-1","measuredAt":"%s+09:00","values":[
                 {"dataItemCode":"run_signal","valueType":"STATE","value":1},
                 {"dataItemCode":"prod_count","valueType":"CUMULATIVE","value":%d},
                 {"dataItemCode":"coolant_temp","valueType":"MEASUREMENT","value":%s}]}"""
                .formatted(id, at, counter, temp);
        mvc.perform(post("/internal/v1/measurements").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
    }

    private double latest(long dataItemId) {
        return jdbc.queryForObject("SELECT measured_value FROM measurement_latest WHERE data_item_id = ?",
                Double.class, dataItemId);
    }

    private int produced() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(qty), 0) FROM production_log WHERE equipment_id = 1",
                Integer.class);
    }
}

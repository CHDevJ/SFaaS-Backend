package com.sfaasproject.stf.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sfaasproject.stf.auth.service.AccessRevocationStore;
import com.sfaasproject.stf.measurement.service.IngestionMessageGuard;
import com.sfaasproject.stf.operations.port.BPorts;
import com.sfaasproject.stf.telegram.service.TelegramDeliveryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
 * INT-001 동시 수집. 커밋 후 outbox 발행이 트랜잭션 연결을 쥔 채 두 번째 연결을 열면, 동시 요청이 풀 크기를 넘을 때
 * 모든 연결이 서로를 기다리며 멈춘다(2026-11-18 통합 점검: 설비 27대 동시 전송에서 Hikari active=10, waiting=202).
 * 풀을 2개로 줄이고 설비 8대가 동시에 보내도 모두 받아들여지고, 모든 이벤트가 발행되는지 확인한다.
 */
@SpringBootTest(properties = {
    "stf.backend-b.enabled=true",
    "stf.backend-b.jobs-enabled=false",
    "stf.backend-b.ingestion-enabled=true",
    "stf.telegram.webhook-secret=concurrency-test-callback",
    "INTERNAL_API_KEY=concurrency-test-internal-key-0123456789",
    "spring.datasource.url=jdbc:h2:mem:ingestion-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.connection-timeout=3000",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import(IngestionConcurrencyTest.FixedClock.class)
class IngestionConcurrencyTest {

    static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 18, 12, 0);
    static final String KEY = "concurrency-test-internal-key-0123456789";
    static final int MACHINES = 8;

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

    ExecutorService pool;

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
        for (int i = 1; i <= MACHINES; i++) {
            jdbc.update("INSERT INTO equipment(equipment_id, equip_code, name, equip_type, process_id, comm_type)"
                    + " VALUES (?, ?, '연삭기', '연삭기', 1, 'MQTT')", i, "OR-04-" + i);
            jdbc.update("INSERT INTO data_item(data_item_id, equipment_id, item_code, name, item_kind, item_role,"
                    + " decimal_places, sort_order) VALUES (?, ?, 'run_signal', '가동 신호', 'STATE', 'RUN_SIGNAL', 0, 1)",
                    i, i);
        }
        pool = Executors.newFixedThreadPool(MACHINES);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void concurrentIngestionDoesNotStarveTheConnectionPool() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 1; i <= MACHINES; i++) {
            String body = """
                    {"messageId":"c%d","equipmentCode":"OR-04-%d","measuredAt":"%s+09:00",
                     "values":[{"dataItemCode":"run_signal","valueType":"STATE","value":1}]}"""
                    .formatted(i, i, NOON);
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(post("/internal/v1/measurements").header("X-Internal-Api-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(60, TimeUnit.SECONDS));
        }

        assertThat(statuses).as("풀 크기(2)보다 많은 동시 수집도 모두 받아들인다").containsOnly(202);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM measurement_latest", Integer.class)).isEqualTo(MACHINES);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM backend_event_outbox WHERE published_at IS NULL",
                Integer.class)).as("커밋 후 이벤트도 모두 발행된다").isZero();
    }
}

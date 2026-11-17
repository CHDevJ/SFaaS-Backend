package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.auth.service.AccessRevocationStore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;

@SpringBootTest(
        properties = {
            "stf.backend-b.enabled=true",
            "stf.backend-b.jobs-enabled=true",
            "stf.backend-b.assembly-stop-delay=100",
            "stf.backend-b.communication-delay=86400000",
            "stf.backend-b.inventory-delay=86400000",
            "spring.datasource.url=jdbc:h2:mem:b-stop-scheduling;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
        })
class AssemblyStopSchedulingIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean AccessRevocationStore revocations;

    @Test
    void schedulerObservesExternalStockChangesWithoutAnyAEvent() throws Exception {
        var now = LocalDateTime.now();
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "INSERT INTO line(line_id,name,line_type) VALUES"
                                        + " (1,'부품','PART'),(2,'조립','ASSEMBLY')");
                            jdbc.update(
                                    "INSERT INTO process(process_id,line_id,process_seq,name)"
                                        + " VALUES (1,1,1,'부품 끝'),(2,2,1,'조립 끝')");
                            jdbc.update(
                                    "INSERT INTO"
                                        + " equipment(equipment_id,equip_code,name,equip_type,process_id,comm_type,registered_at)"
                                        + " VALUES"
                                        + " (1,'PART-END','부품','검사기',1,'MQTT',?),(2,'AS-04-1','조립','검사기',2,'MQTT',?)",
                                    now,
                                    now);
                            jdbc.update(
                                    "INSERT INTO"
                                        + " equipment_state(equipment_id,status,status_since,last_received_at)"
                                        + " VALUES (1,'RUNNING',?,?),(2,'RUNNING',?,?)",
                                    now,
                                    now,
                                    now,
                                    now);
                            jdbc.update(
                                    "INSERT INTO stock_history(line_id,recorded_at,qty) VALUES"
                                        + " (1,?,0)",
                                    now.minusSeconds(1));
                            jdbc.update(
                                    "INSERT INTO"
                                        + " system_setting(setting_key,name,setting_group,setting_value)"
                                        + " VALUES"
                                        + " ('HOURLY_OUTPUT','계획생산','CALC','360'),('UNIT_PRICE','단가','CALC','50000')");
                        });
        await(() -> jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class) == 1);
        jdbc.update("UPDATE equipment_state SET status='COMMUNICATION_LOST' WHERE equipment_id=2");
        jdbc.update("UPDATE stock_history SET qty=1 WHERE line_id=1");
        Thread.sleep(250);
        assertNull(jdbc.queryForObject("SELECT ended_at FROM assembly_stop", Object.class));
        jdbc.update("UPDATE equipment_state SET status='RUNNING' WHERE equipment_id=2");
        await(
                () ->
                        jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM assembly_stop WHERE ended_at IS NOT"
                                            + " NULL",
                                        Integer.class)
                                == 1);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stop", Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT loss_amount FROM assembly_stop", Long.class));
    }

    void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(20);
        assertTrue(
                condition.getAsBoolean(),
                "Scheduled B reconciliation did not finish in five seconds");
    }
}

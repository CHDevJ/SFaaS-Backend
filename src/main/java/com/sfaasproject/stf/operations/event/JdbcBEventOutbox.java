package com.sfaasproject.stf.operations.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/** Durable after-commit dispatch; only known internal event types can be deserialized. */
public class JdbcBEventOutbox {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTx;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final Map<String, Class<?>> TYPES;

    static {
        Map<String, Class<?>> types = new HashMap<>();
        for (var type : BEvents.class.getDeclaredClasses())
            if (type.isRecord()) types.put(type.getSimpleName(), type);
        TYPES = Map.copyOf(types);
    }

    public JdbcBEventOutbox(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.clock = clock;
        this.newTx = new TransactionTemplate(manager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public long append(Object event) {
        if (!TYPES.containsKey(event.getClass().getSimpleName()))
            throw new IllegalArgumentException("Unknown B event type");
        var key = new GeneratedKeyHolder();
        String body = json.writeValueAsString(event);
        jdbc.update(
                c -> {
                    var p =
                            c.prepareStatement(
                                    "INSERT INTO"
                                            + " backend_event_outbox(event_type,payload,created_at)"
                                            + " VALUES (?,?,?)",
                                    Statement.RETURN_GENERATED_KEYS);
                    p.setString(1, event.getClass().getSimpleName());
                    p.setString(2, body);
                    p.setObject(3, LocalDateTime.now(clock));
                    return p;
                },
                key);
        return Objects.requireNonNull(key.getKeys()).values().stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .findFirst()
                .orElseThrow()
                .longValue();
    }

    public void dispatch(long id) {
        try {
            newTx.execute(
                    status -> {
                        var rows =
                                jdbc.queryForList(
                                        "SELECT event_type,payload FROM backend_event_outbox WHERE"
                                            + " outbox_id=? AND published_at IS NULL FOR UPDATE",
                                        id);
                        if (rows.isEmpty()) return null;
                        var row = rows.get(0);
                        var type = TYPES.get(row.get("event_type").toString());
                        if (type == null) throw new IllegalStateException("Unknown event type");
                        publisher.publishEvent(json.readValue(row.get("payload").toString(), type));
                        jdbc.update(
                                "UPDATE backend_event_outbox SET published_at=? WHERE outbox_id=?",
                                LocalDateTime.now(clock),
                                id);
                        return null;
                    });
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("B event {} remains pending for retry", id);
        }
    }

    @Scheduled(fixedDelayString = "${stf.backend-b.outbox-delay:10000}")
    public void retryPending() {
        for (var row :
                jdbc.queryForList(
                        "SELECT outbox_id FROM backend_event_outbox WHERE published_at IS NULL"
                                + " ORDER BY outbox_id LIMIT 100"))
            dispatch(((Number) row.get("outbox_id")).longValue());
    }
}

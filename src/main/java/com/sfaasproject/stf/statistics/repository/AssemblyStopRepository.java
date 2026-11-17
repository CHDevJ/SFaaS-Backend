package com.sfaasproject.stf.statistics.repository;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.statistics.port.AssemblyStopObservationQuery.Cause;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** B-owned assembly_stop mutations. A tables are never written by this repository. */
public class AssemblyStopRepository {
    public record OpenStop(long id, OffsetDateTime startedAt, int hourlyOutput, long unitPrice) {}

    private final JdbcBDatabase db;

    public AssemblyStopRepository(JdbcBDatabase db) {
        this.db = db;
    }

    public void lock() {
        db.update(
                "INSERT INTO backend_job_lock(lock_name) VALUES ('ASSEMBLY_STOP') ON DUPLICATE KEY"
                    + " UPDATE lock_name=lock_name");
        db.rows(
                "SELECT lock_name FROM backend_job_lock WHERE lock_name='ASSEMBLY_STOP' FOR"
                    + " UPDATE");
    }

    public List<OpenStop> open() {
        return db
                .rows(
                        "SELECT stop_id,started_at,hourly_output,unit_price FROM assembly_stop"
                            + " WHERE ended_at IS NULL ORDER BY started_at,stop_id FOR UPDATE")
                .stream()
                .map(
                        r ->
                                new OpenStop(
                                        number(r, "stop_id"),
                                        dateTime(r.get("started_at")),
                                        (int) number(r, "hourly_output"),
                                        number(r, "unit_price")))
                .toList();
    }

    public long start(Cause cause, OffsetDateTime at, int hourly, long price) {
        return db.insertReturning(
                "stop_id",
                "INSERT INTO"
                    + " assembly_stop(started_at,shortage_line_id,cause_equipment_id,cause_alarm_id,hourly_output,unit_price)"
                    + " VALUES (?,?,?,?,?,?)",
                time(at.withNano(0)),
                cause.shortageLineId(),
                cause.equipmentId(),
                cause.alarmId(),
                hourly,
                price);
    }

    public boolean end(OpenStop stop, OffsetDateTime at, BigDecimal amount) {
        return db.update(
                        "UPDATE assembly_stop SET ended_at=?,loss_amount=? WHERE stop_id=? AND"
                            + " ended_at IS NULL",
                        time(at.withNano(0)),
                        amount.longValueExact(),
                        stop.id())
                == 1;
    }
}

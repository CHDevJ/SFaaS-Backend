package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.common.DomainException;
import org.springframework.http.HttpStatus;

import java.time.*;
import java.util.*;

public class JdbcBDuties implements Duties {
    private final JdbcBDatabase db;
    private final EventClaims claims;

    public JdbcBDuties(JdbcBDatabase db, EventClaims claims) {
        this.db = db;
        this.claims = claims;
    }

    @Override
    public void lockAssignee(long userId) {
        db.rows("SELECT account_id FROM account WHERE account_id=? FOR UPDATE", userId);
    }

    @Override
    public List<Duty> excludeAccount(long userId, LocalDate today) {
        db.rows("SELECT lock_name FROM backend_job_lock WHERE lock_name='DUTY' FOR UPDATE");
        var affected = db.rows(
                "SELECT d.duty_id,d.start_date,d.end_date FROM duty d JOIN duty_member m"
                        + " ON m.duty_id=d.duty_id WHERE m.account_id=? AND d.end_date>=?"
                        + " ORDER BY d.start_date FOR UPDATE", userId, today);
        // Validate every period before making any mutation. Historical memberships never block deletion.
        for (var row : affected) {
            long id = number(row, "duty_id");
            var replacement = db.one(
                    "SELECT m.account_id FROM duty_member m JOIN account a ON a.account_id=m.account_id"
                            + " WHERE m.duty_id=? AND m.account_id<>? AND a.deleted_at IS NULL"
                            + " AND a.approval_status='APPROVED' AND a.role='MAINTENANCE'", id, userId);
            if (replacement == null)
                throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "DUTY_REPLACEMENT_REQUIRED",
                        "현재·예정 당번의 대체 보전 담당자를 먼저 지정해 주세요.");
        }
        var removed = new ArrayList<Duty>();
        for (var row : affected) {
            long id = number(row, "duty_id");
            db.update("DELETE FROM duty_member WHERE duty_id=? AND account_id=?", id, userId);
            db.update("UPDATE duty SET updated_at=? WHERE duty_id=?", time(db.now()), id);
            removed.add(new Duty(id, ((java.sql.Date)row.get("start_date")).toLocalDate(),
                    ((java.sql.Date)row.get("end_date")).toLocalDate(), userId));
        }
        return List.copyOf(removed);
    }

    @Override
    public List<Duty> schedules() {
        return select(false);
    }

    @Override
    public List<Duty> lockedSchedules() {
        db.rows("SELECT lock_name FROM backend_job_lock WHERE lock_name='DUTY' FOR UPDATE");
        return select(true);
    }

    private List<Duty> select(boolean lock) {
        return db
                .rows(
                        "SELECT d.duty_id,d.start_date,d.end_date,m.account_id FROM duty d JOIN"
                                + " duty_member m ON m.duty_id=d.duty_id ORDER BY d.start_date"
                                + (lock ? " FOR UPDATE" : ""))
                .stream()
                .map(
                        r ->
                                new Duty(
                                        number(r, "duty_id"),
                                        ((java.sql.Date) r.get("start_date")).toLocalDate(),
                                        ((java.sql.Date) r.get("end_date")).toLocalDate(),
                                        number(r, "account_id")))
                .toList();
    }

    @Override
    public Duty insert(LocalDate from, LocalDate to, long userId) {
        long id = db.insert("INSERT INTO duty(start_date,end_date) VALUES (?,?)", from, to);
        db.update("INSERT INTO duty_member(duty_id,account_id) VALUES (?,?)", id, userId);
        return new Duty(id, from, to, userId);
    }

    @Override
    public void update(Duty d) {
        db.update(
                "UPDATE duty SET start_date=?,end_date=?,updated_at=? WHERE duty_id=?",
                d.from(),
                d.to(),
                time(db.now()),
                d.id());
        db.update("DELETE FROM duty_member WHERE duty_id=?", d.id());
        db.update("INSERT INTO duty_member(duty_id,account_id) VALUES (?,?)", d.id(), d.userId());
    }

    @Override
    public void delete(long id) {
        db.update("DELETE FROM duty_member WHERE duty_id=?", id);
        db.update("DELETE FROM duty WHERE duty_id=?", id);
    }

    @Override
    public boolean claimTransition(LocalDate day, long id) {
        return claims.claim("duty:" + day + ":" + id);
    }
}

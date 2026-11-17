package com.sfaasproject.stf.operations.adapter;

import static com.sfaasproject.stf.operations.adapter.JdbcBDatabase.*;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.port.BPorts.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class JdbcBUsersSettings implements Users, Settings {
    private final JdbcBDatabase db;

    public JdbcBUsersSettings(JdbcBDatabase db) {
        this.db = db;
    }

    @Override
    public UserBrief active(long id) {
        var r =
                db.one(
                        "SELECT account_id,name,role FROM account WHERE account_id=? AND deleted_at"
                                + " IS NULL AND approval_status='APPROVED'",
                        id);
        return r == null
                ? null
                : new UserBrief(
                        number(r, "account_id"),
                        string(r, "name"),
                        Position.valueOf(string(r, "role")));
    }

    @Override
    public UserBrief historical(long id) {
        var r = db.one("SELECT account_id,name,role FROM account WHERE account_id=?", id);
        return r == null || r.get("role") == null
                ? null
                : new UserBrief(
                        number(r, "account_id"),
                        string(r, "name"),
                        Position.valueOf(string(r, "role")));
    }

    @Override
    public boolean connectable(long id) {
        return db.one(
                        "SELECT account_id FROM account WHERE account_id=? AND deleted_at IS NULL",
                        id)
                != null;
    }

    @Override
    public Set<Long> maintenanceIds() {
        return notificationUsers().stream()
                .filter(u -> u.position() == Position.MAINTENANCE)
                .map(UserBrief::userId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<UserBrief> notificationUsers() {
        return db
                .rows(
                        "SELECT account_id,name,role FROM account WHERE deleted_at IS NULL AND"
                                + " approval_status='APPROVED' ORDER BY account_id")
                .stream()
                .map(
                        r ->
                                new UserBrief(
                                        number(r, "account_id"),
                                        string(r, "name"),
                                        Position.valueOf(string(r, "role"))))
                .toList();
    }

    @Override
    public int integer(String key, int fallback) {
        var r = db.one("SELECT setting_value FROM system_setting WHERE setting_key=?", key);
        return r == null ? fallback : Integer.parseInt(string(r, "setting_value"));
    }

    @Override
    public BigDecimal decimal(String key, BigDecimal fallback) {
        var r = db.one("SELECT setting_value FROM system_setting WHERE setting_key=?", key);
        return r == null ? fallback : new BigDecimal(string(r, "setting_value"));
    }
}

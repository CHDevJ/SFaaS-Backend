package com.sfaasproject.stf.system.service;

import static com.sfaasproject.stf.operations.service.V7Support.*;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.operations.adapter.JdbcBDatabase;
import com.sfaasproject.stf.operations.dto.V7Contract.*;
import com.sfaasproject.stf.operations.port.BPorts.UnitOfWork;
import com.sfaasproject.stf.operations.port.V7Context;
import com.sfaasproject.stf.system.entity.AuditTargetType;

import org.springframework.http.HttpStatus;

import java.math.*;
import java.util.*;

public class V7SettingsService {
    /** 변경 이력에 남기는 설정 이름. */
    static final java.util.Map<String, String> SETTING_LABELS = java.util.Map.of(
            "COMM_TIMEOUT_SEC", "통신 끊김 판정(초)",
            "ALARM_STREAK_COUNT", "알람 연속 판정 횟수",
            "STOCK_LEVEL_MIN", "재고 주의 기준(분)",
            "STOCK_DANGER_MIN", "재고 위험 기준(분)",
            "UNIT_PRICE", "개당 금액(원)",
            "RATE_WINDOW_MIN", "속도 계산 구간(분)",
            "HOURLY_OUTPUT", "시간당 생산량(개)");

    private final JdbcBDatabase db;
    private final V7Context context;
    private final UnitOfWork tx;
    private final AuditRecorder audit;

    public V7SettingsService(
            JdbcBDatabase db, V7Context context, UnitOfWork tx, AuditRecorder audit) {
        this.db = db;
        this.context = context;
        this.tx = tx;
        this.audit = audit;
    }

    public JudgementSettingsResponse judgement() {
        return dto(
                map(
                        "success",
                        true,
                        "data",
                        map(
                                "communicationLossSeconds",
                                context.integer("COMM_TIMEOUT_SEC", 30),
                                "alarmConsecutiveCount",
                                context.integer("ALARM_STREAK_COUNT", 3),
                                "inventoryWarningMinutes",
                                context.integer("STOCK_LEVEL_MIN", 30),
                                "inventoryDangerMinutes",
                                context.integer("STOCK_DANGER_MIN", 10),
                                "ranges",
                                List.of(
                                        map(
                                                "field",
                                                "communicationLossSeconds",
                                                "minimum",
                                                10,
                                                "maximum",
                                                300),
                                        map(
                                                "field",
                                                "alarmConsecutiveCount",
                                                "minimum",
                                                1,
                                                "maximum",
                                                10),
                                        map(
                                                "field",
                                                "inventoryWarningMinutes",
                                                "minimum",
                                                5,
                                                "maximum",
                                                120),
                                        map(
                                                "field",
                                                "inventoryDangerMinutes",
                                                "minimum",
                                                1,
                                                "maximum",
                                                60)))),
                JudgementSettingsResponse.class);
    }

    public CalculationSettingsResponse calculation() {
        return dto(
                map(
                        "success",
                        true,
                        "data",
                        map(
                                "unitPrice",
                                context.decimal("UNIT_PRICE", BigDecimal.ZERO),
                                "speedWindowMinutes",
                                context.integer("RATE_WINDOW_MIN", 10),
                                "hourlyProduction",
                                context.integer("HOURLY_OUTPUT", 360),
                                // 입력 범위(CalculationSettingsUpdateRequest 검증과 같음). 2026-11-18 A 추가
                                "ranges",
                                List.of(
                                        map("field", "unitPrice", "minimum", 0, "maximum", 100000000),
                                        map("field", "speedWindowMinutes", "minimum", 5, "maximum", 60),
                                        map("field", "hourlyProduction", "minimum", 1, "maximum", 1000)))),
                CalculationSettingsResponse.class);
    }

    public JudgementSettingsResponse update(JudgementSettings s, long actor) {
        if (s.inventoryDangerMinutes() >= s.inventoryWarningMinutes())
            throw new DomainException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "SETTING_OUT_OF_RANGE",
                    "위험 기준은 주의 기준보다 작아야 합니다.");
        return tx.execute(
                () -> {
                    save("COMM_TIMEOUT_SEC", s.communicationLossSeconds(), "JUDGE", actor);
                    save("ALARM_STREAK_COUNT", s.alarmConsecutiveCount(), "JUDGE", actor);
                    save("STOCK_LEVEL_MIN", s.inventoryWarningMinutes(), "JUDGE", actor);
                    save("STOCK_DANGER_MIN", s.inventoryDangerMinutes(), "JUDGE", actor);
                    return judgement();
                });
    }

    public CalculationSettingsResponse update(CalculationSettingsUpdateRequest s, long actor) {
        return tx.execute(
                () -> {
                    save(
                            "UNIT_PRICE",
                            s.unitPrice().stripTrailingZeros().toPlainString(),
                            "CALC",
                            actor);
                    save("RATE_WINDOW_MIN", s.speedWindowMinutes(), "CALC", actor);
                    save("HOURLY_OUTPUT", s.hourlyProduction(), "CALC", actor);
                    return calculation();
                });
    }

    private void save(String key, Object value, String group, long actor) {
        var before =
                db.one(
                        "SELECT setting_value FROM system_setting WHERE setting_key=? FOR UPDATE",
                        key);
        String next = value.toString();
        if (before != null && next.equals(before.get("setting_value"))) return;
        if (db.update(
                        "UPDATE system_setting SET setting_value=?,updated_at=? WHERE"
                            + " setting_key=?",
                        next,
                        com.sfaasproject.stf.operations.adapter.JdbcBDatabase.time(db.now()),
                        key)
                == 0)
            db.update(
                    "INSERT INTO system_setting(setting_key,name,setting_group,setting_value)"
                        + " VALUES (?,?,?,?)",
                    key,
                    key,
                    group,
                    next);
        // 변경 이력(AUD-001) 화면에 그대로 보이므로 설정 키 대신 한글 이름을 남긴다. 2026-11-18 A 보완
        audit.record(
                actor,
                AuditTargetType.SETTING,
                "JUDGE".equals(group) ? "판정 설정" : "계산 기준값",
                SETTING_LABELS.getOrDefault(key, key),
                before == null ? null : Objects.toString(before.get("setting_value")),
                next);
    }
}

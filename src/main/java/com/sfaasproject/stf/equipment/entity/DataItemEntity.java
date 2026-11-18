package com.sfaasproject.stf.equipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * ERD data_item. 설비가 보내는 값 하나(측정값·상태값·누적값)와 그 알람 기준.
 * 항목 코드(item_code)는 수집(INT-001)의 dataItemCode 와 맞추는 값이라 등록한 뒤 바꾸지 않는다.
 * 값 종류(item_kind)도 쌓인 측정 이력과 누적 카운터 계산이 달라지므로 바꾸지 않는다.
 * 용도(item_role)와 누적 초기화 시각(reset_time)은 계약에 없어 이 엔티티로 다루지 않는다(DB 기본값 유지).
 */
@Entity
@Table(name = "data_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataItemEntity {

    public static final String MEASUREMENT = "MEASUREMENT";
    public static final String STATE = "STATE";
    public static final String CUMULATIVE = "CUMULATIVE";

    /** 측정값·상태값·누적값 공통 입력과 종류별 기준. 쓰지 않는 칸은 null 이다. */
    public record Spec(String name, String unit, int collectCycleSec, int decimalPlaces,
            Double cautionLimit, Double criticalLimit, String limitDirection,
            String stateLabel0, String stateLabel1, Integer alarmStateValue, String alarmStateGrade) { }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_item_id")
    private Long id;

    @Column(name = "equipment_id", nullable = false, updatable = false)
    private Long equipmentId;

    @Column(name = "item_code", length = 50, nullable = false, updatable = false)
    private String itemCode;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "item_kind", length = 20, nullable = false, updatable = false)
    private String itemKind;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "collect_cycle_sec", nullable = false)
    private int collectCycleSec;

    @Column(name = "decimal_places", nullable = false)
    private int decimalPlaces;

    @Column(name = "caution_limit")
    private Double cautionLimit;

    @Column(name = "critical_limit")
    private Double criticalLimit;

    @Column(name = "limit_direction", length = 10)
    private String limitDirection;

    @Column(name = "state_label_0", length = 20)
    private String stateLabel0;

    @Column(name = "state_label_1", length = 20)
    private String stateLabel1;

    /** DB 는 tinyint 다. ddl-auto=validate 가 맞춰 보도록 타입을 적는다. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "alarm_state_value")
    private Integer alarmStateValue;

    @Column(name = "alarm_state_grade", length = 20)
    private String alarmStateGrade;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DataItemEntity register(long equipmentId, String itemCode, String itemKind, Spec spec,
            int sortOrder, LocalDateTime now) {
        DataItemEntity item = new DataItemEntity();
        item.equipmentId = equipmentId;
        item.itemCode = itemCode;
        item.itemKind = itemKind;
        item.sortOrder = sortOrder;
        item.createdAt = now;
        item.apply(spec, now);
        return item;
    }

    public Spec spec() {
        return new Spec(name, unit, collectCycleSec, decimalPlaces, cautionLimit, criticalLimit, limitDirection,
                stateLabel0, stateLabel1, alarmStateValue, alarmStateGrade);
    }

    public void apply(Spec spec, LocalDateTime now) {
        this.name = spec.name();
        this.unit = spec.unit();
        this.collectCycleSec = spec.collectCycleSec();
        this.decimalPlaces = spec.decimalPlaces();
        this.cautionLimit = spec.cautionLimit();
        this.criticalLimit = spec.criticalLimit();
        this.limitDirection = spec.limitDirection();
        this.stateLabel0 = spec.stateLabel0();
        this.stateLabel1 = spec.stateLabel1();
        this.alarmStateValue = spec.alarmStateValue();
        this.alarmStateGrade = spec.alarmStateGrade();
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

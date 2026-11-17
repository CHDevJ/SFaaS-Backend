package com.sfaasproject.stf.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * ERD E3 change_log. 누가 언제 무엇을 바꿨는지 쌓는 이력이다. 줄은 추가만 하고 고치지 않는다.
 * 비밀번호와 토큰 같은 비밀 값은 기록하지 않는다.
 */
@Entity
@Table(name = "change_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeLogEntity {

    public static final int NAME_MAX = 100;
    public static final int FIELD_MAX = 50;
    public static final int VALUE_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "change_id")
    private Long id;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "target_type", length = 20, nullable = false)
    private AuditTargetType targetType;

    @Column(name = "target_name", length = NAME_MAX, nullable = false)
    private String targetName;

    @Column(name = "field_name", length = FIELD_MAX, nullable = false)
    private String fieldName;

    @Column(name = "old_value", length = VALUE_MAX)
    private String oldValue;

    @Column(name = "new_value", length = VALUE_MAX)
    private String newValue;

    public ChangeLogEntity(LocalDateTime changedAt, Long changedBy, AuditTargetType targetType,
            String targetName, String fieldName, String oldValue, String newValue) {
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.targetType = targetType;
        this.targetName = targetName;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}

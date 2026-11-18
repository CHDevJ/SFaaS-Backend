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

/**
 * ERD line. 부품 라인과 조립 라인. 삭제는 줄을 지우지 않고 deleted_at 을 채운다.
 * 재고 계산용 컬럼(consume_per_min, max_stock)은 B 영역이 JDBC 로 읽으므로 여기서는 매핑하지 않는다.
 */
@Entity
@Table(name = "line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineEntity {

    /** v7 LineType. MST-006 에서 lineType 을 생략하면 부품 라인(PART)으로 만든다(v7 제안값). */
    public static final String TYPE_PART = "PART";
    public static final String TYPE_ASSEMBLY = "ASSEMBLY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_id")
    private Long id;

    @Column(name = "name", length = 50, nullable = false, unique = true)
    private String name;

    @Column(name = "line_type", length = 20, nullable = false)
    private String lineType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private LineEntity(String name, String lineType, int sortOrder, LocalDateTime now) {
        this.name = name;
        this.lineType = lineType;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static LineEntity create(String name, String lineType, int sortOrder, LocalDateTime now) {
        return new LineEntity(name, lineType, sortOrder, now);
    }

    public void update(String name, String lineType, int sortOrder, LocalDateTime now) {
        this.name = name;
        this.lineType = lineType;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public void markDeleted(LocalDateTime now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

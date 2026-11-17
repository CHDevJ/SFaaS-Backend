package com.sfaasproject.stf.equipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * ERD process. 라인 안의 공정과 순서. 공정을 만들거나 바꾸는 API 는 계약에 없으므로 읽기 전용으로 매핑한다.
 */
@Entity
@Immutable
@Table(name = "process")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessEntity {

    @Id
    @Column(name = "process_id")
    private Long id;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    /** 라인 안에서 몇 번째 공정인지. API 의 processOrder. */
    @Column(name = "process_seq", nullable = false)
    private int processSeq;

    @Column(name = "name", length = 100, nullable = false)
    private String name;
}

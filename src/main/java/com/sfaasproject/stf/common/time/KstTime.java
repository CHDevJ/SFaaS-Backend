package com.sfaasproject.stf.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 한국 시간 변환 도구. ERD 규칙 8에 따라 DB의 DATETIME은 한국 시간으로 저장하고,
 * API의 date-time은 +09:00 오프셋을 붙여 응답한다.
 */
public final class KstTime {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private KstTime() {
    }

    public static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, KST);
    }

    public static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(KST).toInstant();
    }

    public static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, KST);
    }
}

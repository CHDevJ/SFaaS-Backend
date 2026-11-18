package com.sfaasproject.stf.system.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.entity.ChangeLogEntity;
import com.sfaasproject.stf.system.repository.ChangeLogRepository;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUD-001 설정 변경 이력 (openapi v7, ADMIN 만).
 *
 * <ul>
 *   <li>기간은 from 이상 to 미만이다(화면의 종료일은 다음날 0시로 보낸다, API_SPEC 3장).</li>
 *   <li>itemType 은 change_log.target_type 코드(AuditTargetType)이고 한글 표시는 화면이 바꾼다(2026-11-18 결정).</li>
 *   <li>keyword 는 대상·바꾼 항목·이전 값·변경 값에서 대소문자 없이 찾는다. %·_ 는 글자 그대로 찾는다(! 로 이스케이프).</li>
 *   <li>최근 변경부터(changedAt DESC, historyId DESC). 변경자가 나중에 삭제된 계정이어도 이름과 권한을 보여 준다.</li>
 * </ul>
 */
@Service
public class ChangeHistoryService {

    static final int KEYWORD_MAX = 100;

    /** 변경자 (openapi UserBrief). */
    public record Changer(long userId, String name, String permission) { }

    /** 이력 한 줄 (openapi ChangeHistory + fieldName). 시각은 한국 시간이다. */
    public record History(long historyId, LocalDateTime changedAt, Changer changer, String itemType, String itemName,
            String fieldName, String previousValue, String changedValue) { }

    private final ChangeLogRepository changes;
    private final AccountRepository accounts;

    public ChangeHistoryService(ChangeLogRepository changes, AccountRepository accounts) {
        this.changes = changes;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Page<History> search(OffsetDateTime from, OffsetDateTime to, String itemType, String keyword,
            PageQuery page) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "조회 기간을 확인해 주세요.");
        }
        AuditTargetType type = type(itemType);
        String pattern = pattern(keyword);
        Page<ChangeLogEntity> found = changes.search(KstTime.toLocal(from.toInstant()), KstTime.toLocal(to.toInstant()),
                type, pattern, page.toPageable(Sort.unsorted()));
        Map<Long, AccountEntity> changers = accounts.findAllById(found.getContent().stream()
                        .map(ChangeLogEntity::getChangedBy).distinct().toList())
                .stream().collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        return found.map(change -> {
            AccountEntity account = changers.get(change.getChangedBy());
            Changer changer = account == null ? new Changer(change.getChangedBy(), "알 수 없음", "USER")
                    : new Changer(account.getId(), account.getName(),
                            account.getRole() == null ? "USER" : account.getRole());
            return new History(change.getId(), change.getChangedAt(), changer, change.getTargetType().name(),
                    change.getTargetName(), change.getFieldName(), change.getOldValue(), change.getNewValue());
        });
    }

    private static AuditTargetType type(String itemType) {
        if (itemType == null || itemType.isBlank()) {
            return null;
        }
        return Arrays.stream(AuditTargetType.values()).filter(t -> t.name().equals(itemType.strip())).findFirst()
                .orElseThrow(() -> new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "입력값을 확인해 주세요."));
    }

    /**
     * LIKE 패턴. ! % _ 는 ! 로 막아 글자 그대로 찾는다(escape '!'). 비어 있으면 null(조건 없음).
     * Spring Data 가 선언 쿼리를 다시 쓰면서 escape 의 역슬래시를 지우므로 ! 를 쓴다.
     */
    static String pattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String clean = keyword.strip();
        if (clean.length() > KEYWORD_MAX) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
        }
        String escaped = clean.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}

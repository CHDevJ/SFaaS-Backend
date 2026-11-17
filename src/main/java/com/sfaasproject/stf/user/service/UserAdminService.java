package com.sfaasproject.stf.user.service;

import com.sfaasproject.stf.auth.service.AuthPolicyService;
import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.common.paging.PageQuery;
import com.sfaasproject.stf.common.paging.SortQuery;
import com.sfaasproject.stf.common.time.KstTime;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.event.AccountDeletedEvent;
import com.sfaasproject.stf.user.event.AccountPositionChangedEvent;
import com.sfaasproject.stf.user.repository.AccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USR-001~004 계정 목록, 상세, 수정, 삭제. 대상은 승인되고 삭제되지 않은 계정이다.
 * 승인 대기 계정은 가입 신청(APR)에서 다루므로 여기서는 없는 계정으로 본다.
 * 수정과 삭제는 줄을 잠그고 처리하며 변경 이력을 같은 트랜잭션에 남긴다.
 */
@Service
public class UserAdminService {

    public record UserFilter(String keyword, Position position, Permission permission, AccountStatus status) { }

    static final int KEYWORD_MAX = 100;
    /** ERD account.name 은 varchar(50) 이다. */
    static final int NAME_MAX = 50;

    /** API 의 sort 필드 이름 -> 엔티티 속성. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name", "loginId", "loginId", "position", "role",
            "createdAt", "createdAt", "updatedAt", "updatedAt", "lastLoginAt", "lastLoginAt");
    private static final Sort DEFAULT_SORT = Sort.by("name").ascending();

    private final AccountRepository accounts;
    private final AuthPolicyService policy;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserAdminService(AccountRepository accounts, AuthPolicyService policy, AuditRecorder audit,
            ApplicationEventPublisher events, Clock clock) {
        this.accounts = accounts;
        this.policy = policy;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(UserFilter filter, PageQuery page, String sort) {
        String keyword = filter.keyword() == null ? "" : filter.keyword().strip();
        if (keyword.length() > KEYWORD_MAX) {
            throw validationError();
        }
        Pageable pageable = page.toPageable(SortQuery.parse(sort, SORT_FIELDS, DEFAULT_SORT, "id"));
        List<String> roles = roles(filter.position(), filter.permission());
        List<Boolean> passwordFlags = passwordFlags(filter.status());
        if (roles.isEmpty() || passwordFlags.isEmpty()) {
            return Page.empty(pageable);
        }
        return accounts.searchApproved(roles, passwordFlags, likePattern(keyword), pageable)
                .map(account -> UserView.from(account, policy));
    }

    @Transactional(readOnly = true)
    public UserView get(long userId) {
        return accounts.findById(userId).filter(UserAdminService::manageable)
                .map(account -> UserView.from(account, policy)).orElseThrow(UserAdminService::notFound);
    }

    @Transactional
    public UserView update(long userId, String name, Position position, long actorId) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.length() > NAME_MAX || position == null) {
            throw validationError();
        }
        if (position != Position.SYSTEM_ADMIN) {
            guardLastSystemAdmin(userId);
        }
        AccountEntity account = lock(userId);
        String oldName = account.getName();
        Position oldPosition = Position.valueOf(account.getRole());
        boolean nameChanged = !oldName.equals(cleanName);
        boolean positionChanged = oldPosition != position;
        if (nameChanged || positionChanged) {
            account.updateProfile(cleanName, position.name(), now());
            if (nameChanged) {
                audit.record(actorId, AuditTargetType.ACCOUNT, account.getLoginId(), "이름", oldName, cleanName);
            }
            if (positionChanged) {
                audit.record(actorId, AuditTargetType.ACCOUNT, account.getLoginId(), "직책",
                        PositionLabels.of(oldPosition), PositionLabels.of(position));
                events.publishEvent(new AccountPositionChangedEvent(account.getId(),
                        oldPosition.name(), position.name(), actorId));
            }
        }
        return UserView.from(account, policy);
    }

    /**
     * 줄을 지우지 않고 삭제 시각을 채운다. 비밀번호와 텔레그램 연결 정보는 비운다.
     * 삭제된 계정의 Access Token 과 Refresh Token 은 인증 단계에서 계정 상태 확인으로 바로 거부된다.
     */
    @Transactional
    public void delete(long userId, long actorId) {
        if (userId == actorId) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "USER_SELF_DELETE_FORBIDDEN",
                    "자신의 계정은 삭제할 수 없습니다.");
        }
        guardLastSystemAdmin(userId);
        AccountEntity account = lock(userId);
        String role = account.getRole();
        account.markDeleted(now());
        audit.record(actorId, AuditTargetType.ACCOUNT, account.getLoginId(), "계정 삭제", "사용", "삭제");
        events.publishEvent(new AccountDeletedEvent(account.getId(), account.getLoginId(), role, actorId));
    }

    /**
     * 시스템관리자는 항상 한 명 이상 있어야 한다. 대상이 마지막 시스템관리자라면 직책을 바꾸거나 삭제할 수 없고,
     * 먼저 다른 사람을 시스템관리자로 지정해야 한다. 시스템관리자 행을 모두 잠근 뒤 세므로 동시에 들어온
     * 요청도 차례로 검사된다(먼저 처리된 쪽의 결과를 보고 판단한다).
     */
    private void guardLastSystemAdmin(long userId) {
        List<AccountEntity> admins = accounts.lockActiveSystemAdmins();
        boolean targetIsAdmin = admins.stream().anyMatch(admin -> admin.getId() == userId);
        if (targetIsAdmin && admins.size() <= 1) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "LAST_SYSTEM_ADMIN_REQUIRED",
                    "시스템관리자는 최소 한 명 있어야 합니다. 다른 사람을 먼저 시스템관리자로 지정해 주세요.");
        }
    }

    private AccountEntity lock(long userId) {
        return accounts.findForUpdate(userId).filter(UserAdminService::manageable)
                .orElseThrow(UserAdminService::notFound);
    }

    private static boolean manageable(AccountEntity account) {
        return !account.isDeleted() && !account.isPending();
    }

    /** 직책과 권한 조건을 함께 만족하는 직책 목록. 권한은 직책에서 파생된다(SYSTEM_ADMIN 만 ADMIN). */
    private List<String> roles(Position position, Permission permission) {
        List<String> roles = new ArrayList<>();
        for (Position candidate : Position.values()) {
            if ((position == null || position == candidate)
                    && (permission == null || policy.permission(candidate) == permission)) {
                roles.add(candidate.name());
            }
        }
        return roles;
    }

    private static List<Boolean> passwordFlags(AccountStatus status) {
        if (status == null) {
            return List.of(true, false);
        }
        return switch (status) {
            case ACTIVE -> List.of(false);
            case PASSWORD_CHANGE_REQUIRED -> List.of(true);
            case PENDING_APPROVAL -> List.of(); // 승인 대기는 가입 신청 목록에서 다룬다.
        };
    }

    /** 검색어를 글자 그대로 찾도록 LIKE 의 특수문자를 '!' 로 이스케이프한다. */
    static String likePattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private LocalDateTime now() {
        return KstTime.toLocal(clock.instant());
    }

    private static DomainException notFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
    }

    private static DomainException validationError() {
        return new DomainException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.");
    }
}

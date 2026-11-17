package com.sfaasproject.stf.user.entity;

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
 * ERD A1 account. 로그인하는 사람과 알람을 받는 사람이며 가입 신청도 이 테이블에 저장한다.
 * 삭제는 줄을 지우지 않고 deleted_at 을 채운다.
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Column(name = "login_id", length = 50, nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "telegram_chat_id", length = 30, unique = true)
    private String telegramChatId;

    @Column(name = "telegram_linked_at")
    private LocalDateTime telegramLinkedAt;

    /** 직책(SYSTEM_ADMIN, LINE_MANAGER, MAINTENANCE). 가입을 승인할 때 정하므로 승인 전에는 비어 있다. */
    @Column(name = "role", length = 20)
    private String role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "approval_status", length = 20, nullable = false)
    private ApprovalStatus approvalStatus;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private AccountEntity(String loginId, String passwordHash, String name, LocalDateTime now) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.approvalStatus = ApprovalStatus.PENDING;
        this.mustChangePassword = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 회원가입 신청. 승인 대기 상태이고 직책은 비어 있다. {@code passwordHash}는 암호화된 값이어야 한다. */
    public static AccountEntity signup(String loginId, String passwordHash, String name,
            LocalDateTime now) {
        return new AccountEntity(loginId, passwordHash, name, now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isPending() {
        return approvalStatus == ApprovalStatus.PENDING;
    }

    public boolean isTelegramConnected() {
        return telegramChatId != null;
    }

    public void recordLogin(LocalDateTime at) {
        this.lastLoginAt = at;
        this.updatedAt = at;
    }

    /** 새 비밀번호를 저장하고 비밀번호 변경 필요 표시를 끈다. */
    public void changePassword(String newPasswordHash, LocalDateTime now) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
        this.updatedAt = now;
    }

    /** 가입 승인. 직책을 정하고 승인 상태로 바꾼다. */
    public void approve(String role, Long approvedBy, LocalDateTime now) {
        this.role = role;
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.approvedAt = now;
        this.approvedBy = approvedBy;
        this.updatedAt = now;
    }

    /** 텔레그램 봇과 연결한 대화 번호를 저장한다. 연결해야 텔레그램 알림을 받는다. */
    public void connectTelegram(String chatId, LocalDateTime now) {
        this.telegramChatId = chatId;
        this.telegramLinkedAt = now;
        this.updatedAt = now;
    }

    public void markDeleted(LocalDateTime now) {
        this.passwordHash = null;
        this.telegramChatId = null;
        this.telegramLinkedAt = null;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public void requirePasswordChange(LocalDateTime now) {
        this.mustChangePassword = true;
        this.updatedAt = now;
    }
}

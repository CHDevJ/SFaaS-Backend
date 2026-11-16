package com.sfaasproject.stf.auth.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Authentication input and role rules from OpenAPI v5. */
@Service
public class AuthPolicyService {
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9]{4,20}$");
    private static final Pattern PASSWORD = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$");

    public enum Position { SYSTEM_ADMIN, LINE_MANAGER, MAINTENANCE }
    public enum Permission { ADMIN, GENERAL }
    public enum AccountStatus { PENDING_APPROVAL, ACTIVE, PASSWORD_CHANGE_REQUIRED }

    public void validateLoginId(String loginId) {
        if (loginId == null || !LOGIN_ID.matcher(loginId).matches()) {
            throw new IllegalArgumentException("Login ID must be 4-20 lowercase letters or digits");
        }
    }

    public void validatePassword(String password) {
        if (password == null || !PASSWORD.matcher(password).matches()) {
            throw new IllegalArgumentException("Password does not meet policy");
        }
    }

    public Permission permission(Position position) {
        if (position == null) throw new IllegalArgumentException("Position is required");
        return position == Position.SYSTEM_ADMIN ? Permission.ADMIN : Permission.GENERAL;
    }
}

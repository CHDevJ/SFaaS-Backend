package com.sfaasproject.stf.auth.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Authentication input and role rules from OpenAPI v7. */
@Service
public class AuthPolicyService {
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9]{4,20}$");
    private static final Pattern PASSWORD = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$");

    public enum Permission { ADMIN, USER }
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

}

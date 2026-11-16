package com.sfaasproject.stf.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import org.junit.jupiter.api.Test;

class AuthPolicyServiceTest {
    private final AuthPolicyService policy = new AuthPolicyService();

    @Test
    void validatesContractLoginAndPassword() {
        assertDoesNotThrow(() -> policy.validateLoginId("parkjh1"));
        assertDoesNotThrow(() -> policy.validatePassword("Password1!"));
        assertThrows(IllegalArgumentException.class, () -> policy.validateLoginId("Parkjh1"));
        assertThrows(IllegalArgumentException.class, () -> policy.validateLoginId("abc"));
        assertThrows(IllegalArgumentException.class, () -> policy.validatePassword("password1"));
        assertThrows(IllegalArgumentException.class, () -> policy.validatePassword("Password!"));
        assertThrows(IllegalArgumentException.class, () -> policy.validatePassword("Pass1\nword!"));
    }

    @Test
    void derivesReadOnlyPermissionFromPosition() {
        assertEquals(Permission.ADMIN, policy.permission(Position.SYSTEM_ADMIN));
        assertEquals(Permission.GENERAL, policy.permission(Position.LINE_MANAGER));
        assertEquals(Permission.GENERAL, policy.permission(Position.MAINTENANCE));
    }
}

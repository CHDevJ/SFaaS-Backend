package com.sfaasproject.stf.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
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
    void permissionsAreTwoDirectStoredValues() {
        assertArrayEquals(new Permission[]{Permission.ADMIN,Permission.USER},Permission.values());
    }
}

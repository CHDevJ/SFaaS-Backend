package com.sfaasproject.stf.user.service;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;

/** 변경 이력에 남길 직책의 한글 이름. */
final class PositionLabels {

    private PositionLabels() {
    }

    static String of(Permission position) {
        return switch (position) {
            case ADMIN -> "관리자";
            case USER -> "사용자";
        };
    }
}

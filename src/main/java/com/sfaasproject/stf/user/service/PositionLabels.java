package com.sfaasproject.stf.user.service;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;

/** 변경 이력에 남길 직책의 한글 이름. */
final class PositionLabels {

    private PositionLabels() {
    }

    static String of(Position position) {
        return switch (position) {
            case SYSTEM_ADMIN -> "시스템관리자";
            case LINE_MANAGER -> "라인 관리자";
            case MAINTENANCE -> "보전 담당자";
        };
    }
}

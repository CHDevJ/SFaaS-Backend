package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.operations.dto.BContract.Position;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

public final class BAccess {
    private BAccess() {}

    public record Actor(long userId, Position position) {}

    public static Actor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw unauthorized();
        try {
            long id = Long.parseLong(auth.getName());
            if (id <= 0) throw unauthorized();
            for (var authority : auth.getAuthorities()) {
                String role = authority.getAuthority().replaceFirst("^ROLE_", "");
                for (Position p : Position.values())
                    if (p.name().equals(role)) return new Actor(id, p);
            }
        } catch (NumberFormatException ex) {
            throw unauthorized();
        }
        throw unauthorized();
    }

    public static Actor admin() {
        Actor actor = current();
        if (actor.position() != Position.SYSTEM_ADMIN)
            throw new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "관리자만 처리할 수 있습니다.");
        return actor;
    }

    private static DomainException unauthorized() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "다시 로그인해 주세요.");
    }
}

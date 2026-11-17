package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;
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
            long id = auth.getPrincipal() instanceof Claims claims
                    ? claims.userId() : Long.parseLong(auth.getName());
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

    public static Actor current(Claims principal) {
        if (principal == null) throw unauthorized();
        var actor = current();
        if (actor.userId() != principal.userId()
                || !actor.position().name().equals(principal.position().name())) throw unauthorized();
        return actor;
    }

    public static Actor admin(Claims principal) {
        current(principal);
        return admin();
    }

    public static Actor admin() {
        Actor actor = current();
        if (actor.position() != Position.SYSTEM_ADMIN)
            throw new DomainException(HttpStatus.FORBIDDEN, "AUTH_PERMISSION_DENIED", "관리자만 처리할 수 있습니다.");
        return actor;
    }

    private static DomainException unauthorized() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID", "다시 로그인해 주세요.");
    }
}

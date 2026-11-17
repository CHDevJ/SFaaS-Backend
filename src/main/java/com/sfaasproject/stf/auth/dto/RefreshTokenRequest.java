package com.sfaasproject.stf.auth.dto;

/**
 * AUTH-002 요청. 브라우저는 HttpOnly 쿠키를 쓰므로 본문이 없고, 이 값은 브라우저가 아닌 클라이언트용
 * 대체 입력이다. 토큰이 로그에 남지 않도록 {@link #toString()}에서 가린다.
 */
public record RefreshTokenRequest(String refreshToken) {

    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=****]";
    }
}

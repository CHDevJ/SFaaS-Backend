package com.sfaasproject.stf.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 로그인은 했지만 직책이 맞지 않는 요청에 403 AUTH_PERMISSION_DENIED 를 응답한다. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        RestAuthenticationEntryPoint.write(response, objectMapper, HttpStatus.FORBIDDEN,
                "AUTH_PERMISSION_DENIED", "접근 권한이 없습니다.");
    }
}

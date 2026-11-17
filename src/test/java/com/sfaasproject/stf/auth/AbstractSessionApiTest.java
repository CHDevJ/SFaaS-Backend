package com.sfaasproject.stf.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 로그인한 뒤 토큰을 다루는 테스트의 공통 도구(로그인, 쿠키 읽기와 보내기). */
public abstract class AbstractSessionApiTest extends AbstractAuthIntegrationTest {

    protected static final String LOGIN = "/api/v1/auth/login";
    protected static final String REFRESH = "/api/v1/auth/refresh";
    protected static final String LOGOUT = "/api/v1/auth/logout";
    protected static final String ME = "/api/v1/auth/me";
    protected static final String PASSWORD_URL = "/api/v1/auth/me/password";
    protected static final String NEW_PASSWORD = "N3wPassw0rd#z";

    /** 로그인으로 받은 Access Token 과 Refresh Token(쿠키 값). */
    protected record Session(String access, String refresh) {
        public String bearer() {
            return "Bearer " + access;
        }
    }

    protected Session login(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(loginId, password)))
                .andExpect(status().isOk()).andReturn();
        String access = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        return new Session(access, refreshCookieValue(result));
    }

    protected Session login(String loginId) throws Exception {
        return login(loginId, PASSWORD);
    }

    /** 응답의 Set-Cookie 에서 refreshToken 쿠키 값을 꺼낸다. */
    protected static String refreshCookieValue(MvcResult result) {
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String header = cookies.stream().filter(value -> value.startsWith("stfRefreshToken=")).findFirst()
                .orElseThrow(() -> new AssertionError("refreshToken 쿠키가 없다: " + cookies));
        return header.substring("stfRefreshToken=".length(), header.indexOf(';'));
    }

    /** 응답이 refreshToken 쿠키를 지우도록(값 없음, Max-Age=0) 내려갔는지 검사한다. */
    protected static void assertRefreshCookieCleared(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).as("Set-Cookie").isNotNull().startsWith("stfRefreshToken=;")
                .contains("Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
    }

    protected static MockHttpServletRequestBuilder withRefresh(MockHttpServletRequestBuilder request,
            String refreshToken) {
        return request.cookie(new Cookie("stfRefreshToken", refreshToken));
    }
}

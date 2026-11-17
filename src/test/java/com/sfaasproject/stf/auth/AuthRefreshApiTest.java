package com.sfaasproject.stf.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.entity.AuthTokenEntity;
import com.sfaasproject.stf.auth.service.DatabaseSessionStore;
import com.sfaasproject.stf.auth.service.RefreshTokenService;
import com.sfaasproject.stf.user.entity.AccountEntity;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** AUTH-002 토큰 갱신(Refresh Rotation). */
class AuthRefreshApiTest extends AbstractSessionApiTest {

    @Autowired
    private DatabaseSessionStore sessions;
    @Autowired
    private RefreshTokenService refreshTokens;

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(withRefresh(post(REFRESH), refreshToken)).andReturn();
    }

    @Test
    void refreshing_rotates_the_refresh_token_and_issues_a_new_access_token() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        Session session = login("admin001");

        MvcResult result = mockMvc.perform(withRefresh(post(REFRESH), session.refresh()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String newRefresh = refreshCookieValue(result);
        assertThat(newRefresh).isNotEqualTo(session.refresh());
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=604800");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(newRefresh);

        List<AuthTokenEntity> rows = tokens.findAll();
        assertThat(rows).hasSize(2);
        AuthTokenEntity old = tokens.findByTokenHash(refreshTokens.hash(session.refresh())).orElseThrow();
        AuthTokenEntity next = tokens.findByTokenHash(refreshTokens.hash(newRefresh)).orElseThrow();
        assertThat(old.getUsedAt()).as("이전 토큰은 사용됨으로 표시").isNotNull();
        assertThat(old.getRevokedAt()).isNull();
        assertThat(next.getUsedAt()).isNull();
        assertThat(next.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(next.getAccountId()).isEqualTo(admin.getId());
        assertThat(next.getTokenHash()).hasSize(64).isNotEqualTo(newRefresh);

        String newAccess = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.accessToken");
        mockMvc.perform(get(ME).header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
    }

    @Test
    void a_client_without_cookies_can_send_the_refresh_token_in_the_body() throws Exception {
        approvedAccount("admin001", "SYSTEM_ADMIN");
        Session session = login("admin001");

        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.refresh() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void the_cookie_is_used_when_both_the_cookie_and_the_body_are_sent() throws Exception {
        approvedAccount("admin001", "SYSTEM_ADMIN");
        Session session = login("admin001");

        mockMvc.perform(withRefresh(post(REFRESH), session.refresh()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-the-real-token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void reusing_a_rotated_token_is_refused_and_revokes_the_whole_family() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        Session session = login("admin001");
        MvcResult first = refresh(session.refresh());
        String rotated = refreshCookieValue(first);

        MvcResult replay = mockMvc.perform(withRefresh(post(REFRESH), session.refresh()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID"))
                .andReturn();
        assertRefreshCookieCleared(replay);

        mockMvc.perform(withRefresh(post(REFRESH), rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID"));
        assertThat(tokens.findAll()).as("계열 전체가 폐기된다").allSatisfy(row -> {
            assertThat(row.getAccountId()).isEqualTo(admin.getId());
            assertThat(row.getRevokedAt()).isNotNull();
        });
    }

    @Test
    void a_missing_unknown_or_malformed_token_is_refused_and_the_cookie_is_cleared() throws Exception {
        approvedAccount("admin001", "SYSTEM_ADMIN");

        MvcResult none = mockMvc.perform(post(REFRESH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID")).andReturn();
        assertRefreshCookieCleared(none);
        for (String token : new String[] {"garbage", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", " "}) {
            MvcResult result = refresh(token);
            assertThat(result.getResponse().getStatus()).as(token).isEqualTo(401);
            assertThat(result.getResponse().getContentAsString()).contains("AUTH_REFRESH_TOKEN_INVALID");
            assertRefreshCookieCleared(result);
        }
        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void an_expired_refresh_token_is_refused() throws Exception {
        AccountEntity admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        RefreshTokenService.Issued issued = refreshTokens.issue(clock.instant());
        sessions.create(admin.getId(), "family-expired", issued.hash(), clock.instant().minusSeconds(1));

        mockMvc.perform(withRefresh(post(REFRESH), issued.value()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID"));
    }

    @Test
    void a_deleted_account_cannot_refresh_and_its_family_is_revoked() throws Exception {
        AccountEntity account = approvedAccount("gone0001", "MAINTENANCE");
        Session session = login("gone0001");
        account.markDeleted(LocalDateTime.now(clock));
        accounts.save(account);

        mockMvc.perform(withRefresh(post(REFRESH), session.refresh()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID"));

        assertThat(tokens.findAll()).allSatisfy(row -> assertThat(row.getRevokedAt()).isNotNull());
    }

    @Test
    void refreshing_needs_no_access_token_and_ignores_a_stale_authorization_header() throws Exception {
        approvedAccount("admin001", "SYSTEM_ADMIN");
        Session session = login("admin001");

        mockMvc.perform(withRefresh(post(REFRESH), session.refresh())
                        .header("Authorization", "Bearer expired.or.garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void sessions_of_the_same_user_rotate_independently() throws Exception {
        approvedAccount("admin001", "SYSTEM_ADMIN");
        Session first = login("admin001");
        Session second = login("admin001");

        refresh(first.refresh());

        mockMvc.perform(withRefresh(post(REFRESH), second.refresh())).andExpect(status().isOk());
        assertThat(tokens.findAll().stream().map(AuthTokenEntity::getFamilyId).distinct().count()).isEqualTo(2);
    }

    @Test
    void two_simultaneous_refreshes_of_the_same_token_let_only_one_win_and_then_revoke_the_family()
            throws Exception {
        approvedAccount("admin001", "SYSTEM_ADMIN");
        Session session = login("admin001");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return refresh(session.refresh());
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }

            List<Integer> statuses = results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
            assertThat(statuses).containsExactly(200, 401);
            MvcResult winner = results.stream().filter(r -> r.getResponse().getStatus() == 200).findFirst()
                    .orElseThrow();
            // 재사용이 감지되었으므로 이긴 쪽의 새 토큰도 더는 쓸 수 없다.
            mockMvc.perform(withRefresh(post(REFRESH), refreshCookieValue(winner)))
                    .andExpect(status().isUnauthorized());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void refresh_tokens_are_never_written_to_the_response_body() throws Exception {
        approvedAccount("admin001", "SYSTEM_ADMIN");
        Session session = login("admin001");

        MvcResult result = refresh(session.refresh());

        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken")
                .doesNotContain(session.refresh()).doesNotContain(refreshCookieValue(result));
        assertThat(Duration.ofDays(7).toSeconds()).isEqualTo(604800L);
    }
}

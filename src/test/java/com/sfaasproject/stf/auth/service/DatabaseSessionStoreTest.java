package com.sfaasproject.stf.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sfaasproject.stf.auth.AbstractAuthIntegrationTest;
import com.sfaasproject.stf.auth.entity.AuthTokenEntity;
import com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore.Rotation;
import com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore.RotationStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DatabaseSessionStoreTest extends AbstractAuthIntegrationTest {

    private static final long USER = 7L;

    @Autowired
    private DatabaseSessionStore sessions;

    private Instant now() {
        return clock.instant();
    }

    private Instant in(Duration duration) {
        return now().plus(duration);
    }

    private AuthTokenEntity row(String hash) {
        return tokens.findByTokenHash(hash).orElseThrow();
    }

    @Test
    void rotation_marks_the_old_token_used_and_stores_the_next_one_in_the_same_family() {
        sessions.create(USER, "family-1", "hash-a", in(Duration.ofDays(7)));

        Rotation rotation = sessions.rotate("hash-a", "hash-b", in(Duration.ofDays(7)), now());

        assertThat(rotation.status()).isEqualTo(RotationStatus.ROTATED);
        assertThat(rotation.userId()).isEqualTo(USER);
        assertThat(rotation.familyId()).isEqualTo("family-1");
        assertThat(row("hash-a").getUsedAt()).isNotNull();
        assertThat(row("hash-b").getFamilyId()).isEqualTo("family-1");
        assertThat(row("hash-b").getUsedAt()).isNull();
        assertThat(row("hash-b").getAccountId()).isEqualTo(USER);
    }

    @Test
    void a_rotated_token_that_comes_again_is_reported_as_reused_with_its_family() {
        sessions.create(USER, "family-1", "hash-a", in(Duration.ofDays(7)));
        sessions.rotate("hash-a", "hash-b", in(Duration.ofDays(7)), now());

        Rotation reuse = sessions.rotate("hash-a", "hash-c", in(Duration.ofDays(7)), now());

        assertThat(reuse.status()).isEqualTo(RotationStatus.REUSED);
        assertThat(reuse.userId()).isEqualTo(USER);
        assertThat(reuse.familyId()).isEqualTo("family-1");
        assertThat(tokens.findByTokenHash("hash-c")).as("재사용이면 새 토큰을 만들지 않는다").isEmpty();
    }

    @Test
    void unknown_expired_and_revoked_tokens_are_invalid() {
        sessions.create(USER, "family-1", "hash-expired", now().minusSeconds(1));
        sessions.create(USER, "family-2", "hash-revoked", in(Duration.ofDays(7)));
        sessions.revokeFamily("family-2");

        assertThat(sessions.rotate("hash-unknown", "n1", in(Duration.ofDays(7)), now()).status())
                .isEqualTo(RotationStatus.INVALID);
        assertThat(sessions.rotate("hash-expired", "n2", in(Duration.ofDays(7)), now()).status())
                .isEqualTo(RotationStatus.INVALID);
        assertThat(sessions.rotate("hash-revoked", "n3", in(Duration.ofDays(7)), now()).status())
                .isEqualTo(RotationStatus.INVALID);
        assertThat(tokens.count()).isEqualTo(2);
    }

    @Test
    void revoking_a_family_leaves_other_families_alone() {
        sessions.create(USER, "family-1", "hash-a", in(Duration.ofDays(7)));
        sessions.rotate("hash-a", "hash-b", in(Duration.ofDays(7)), now());
        sessions.create(USER, "family-2", "hash-x", in(Duration.ofDays(7)));

        sessions.revokeFamily("family-1");

        assertThat(row("hash-a").getRevokedAt()).isNotNull();
        assertThat(row("hash-b").getRevokedAt()).isNotNull();
        assertThat(row("hash-x").getRevokedAt()).isNull();
    }

    @Test
    void a_family_can_be_revoked_through_any_of_its_token_hashes() {
        sessions.create(USER, "family-1", "hash-a", in(Duration.ofDays(7)));
        sessions.rotate("hash-a", "hash-b", in(Duration.ofDays(7)), now());

        sessions.revokeFamilyByRefreshHash("hash-b");
        sessions.revokeFamilyByRefreshHash("hash-never-issued"); // 없는 값은 조용히 무시

        assertThat(row("hash-a").getRevokedAt()).isNotNull();
        assertThat(row("hash-b").getRevokedAt()).isNotNull();
    }

    @Test
    void revoking_everything_of_a_user_revokes_refresh_tokens_and_old_access_tokens() {
        sessions.create(USER, "family-1", "hash-a", in(Duration.ofDays(7)));
        sessions.create(USER, "family-2", "hash-b", in(Duration.ofDays(7)));
        sessions.create(99L, "family-9", "hash-other", in(Duration.ofDays(7)));
        Instant issuedEarlier = now().minusSeconds(60);

        sessions.revokeAllForUser(USER, now());

        assertThat(row("hash-a").getRevokedAt()).isNotNull();
        assertThat(row("hash-b").getRevokedAt()).isNotNull();
        assertThat(row("hash-other").getRevokedAt()).isNull();
        assertThat(sessions.isAccessRevoked("any-jti", USER, issuedEarlier)).isTrue();
        assertThat(sessions.isAccessRevoked("any-jti", 99L, issuedEarlier)).isFalse();
    }

    @Test
    void a_logged_out_access_token_is_reported_as_revoked() {
        sessions.revokeAccess("jti-1", in(Duration.ofMinutes(30)));

        assertThat(sessions.isAccessRevoked("jti-1", USER, now())).isTrue();
        assertThat(sessions.isAccessRevoked("jti-2", USER, now())).isFalse();
    }

    @Test
    void only_one_of_two_simultaneous_rotations_of_the_same_token_wins() throws Exception {
        sessions.create(USER, "family-1", "hash-a", in(Duration.ofDays(7)));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Rotation>> futures = new ArrayList<>();
            for (String next : new String[] {"hash-b", "hash-c"}) {
                Callable<Rotation> task = () -> {
                    start.await();
                    return sessions.rotate("hash-a", next, in(Duration.ofDays(7)), now());
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<RotationStatus> results = new ArrayList<>();
            for (Future<Rotation> future : futures) {
                results.add(future.get().status());
            }

            assertThat(results).containsExactlyInAnyOrder(RotationStatus.ROTATED, RotationStatus.REUSED);
            assertThat(tokens.count()).as("원래 토큰과 새 토큰 하나만 남는다").isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }
}

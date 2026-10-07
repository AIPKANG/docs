package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.SocialSignupCommand;
import com.team.blog.account.application.SocialSignupService;
import com.team.blog.account.domain.PendingSocialSignup;
import com.team.blog.account.domain.Provider;
import com.team.blog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 001 T154: 같은 대기 정보로 마무리 20건 동시 → 계정 1개(SC-001), 나머지는 그 계정으로 로그인(research R-8). */
class SocialSignupConcurrencyIT extends IntegrationTestBase {

    private static final int THREADS = 20;

    @Autowired
    SocialSignupService socialSignupService;

    @Autowired
    JdbcTemplate jdbc;

    private List<SocialSignupService.Result> race(PendingSocialSignup pending, IntFunction<SocialSignupCommand> command)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<SocialSignupService.Result>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                SocialSignupCommand cmd = command.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return socialSignupService.complete(pending, cmd);
                }));
            }
            ready.await();
            go.countDown();
            List<SocialSignupService.Result> results = new ArrayList<>();
            for (Future<SocialSignupService.Result> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertExactlyOneAccount(List<SocialSignupService.Result> results) {
        assertThat(results).filteredOn(SocialSignupService.Result::created).hasSize(1);
        long memberId = results.stream().filter(SocialSignupService.Result::created).findFirst().orElseThrow().memberId();
        assertThat(results).allMatch(r -> r.memberId() == memberId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_identity", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_agreement", Integer.class)).isEqualTo(2);
    }

    @Test
    void sameSubmissionTwentyTimesHitsMemberHandleFirst() throws Exception {
        PendingSocialSignup pending = new PendingSocialSignup(Provider.GOOGLE, "g-race", "race@gmail.com", "Race", null,
                clock.instant());
        assertExactlyOneAccount(race(pending, n -> new SocialSignupCommand("레이스", "race", true, true, false, null)));
    }

    @Test
    void differentHandlesHitAuthIdentityUniqueness() throws Exception {
        PendingSocialSignup pending = new PendingSocialSignup(Provider.GITHUB, "31337", "race@x.com", "Race", null,
                clock.instant());
        assertExactlyOneAccount(race(pending, n -> new SocialSignupCommand("경주" + (char) ('가' + n), "race_" + n,
                true, true, false, null)));
    }
}

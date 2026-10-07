package com.team.blog.account.integration;

import static com.team.blog.account.integration.AuthTestSupport.signup;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 001 T122: 동시 가입 20건(SC-001, FR-002) — DB UNIQUE가 최종 보장, 진 쪽은 안내로 번역. */
class EmailSignupConcurrencyIT extends IntegrationTestBase {

    private static final int THREADS = 20;

    @Autowired
    JdbcTemplate jdbc;

    record Outcome(int status, String body) {
    }

    private List<Outcome> race(IntFunction<MockHttpServletRequestBuilder> request) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int n = i;
                Callable<Outcome> task = () -> {
                    MockHttpServletRequestBuilder builder = request.apply(n);
                    ready.countDown();
                    go.await();
                    MvcResult result = mockMvc.perform(builder).andReturn();
                    return new Outcome(result.getResponse().getStatus(), result.getResponse().getContentAsString());
                };
                futures.add(pool.submit(task));
            }
            ready.await();
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String nickname(int n) {
        return "동시" + (char) ('가' + n);
    }

    @Test
    void sameEmailTwentyTimesCreatesExactlyOneAccount() throws Exception {
        List<Outcome> outcomes = race(n -> signup("race@x.com", "race_" + n, nickname(n)));
        assertThat(outcomes).filteredOn(o -> o.status() == 303).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.status() == 400).hasSize(THREADS - 1)
                .allMatch(o -> o.body().contains("이미 가입된 이메일이에요."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_identity", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameHandleFromDifferentEmailsGivesOneWinnerAndSuggestsAlternative() throws Exception {
        List<Outcome> outcomes = race(n -> signup("h" + n + "@x.com", "samehandle", nickname(n)));
        assertThat(outcomes).filteredOn(o -> o.status() == 303).hasSize(1);
        // 사전 검사에서 걸리면 400(이미 사용 중 + 대안), 경합에서 지면 409(방금 다른 분이…)
        assertThat(outcomes).filteredOn(o -> o.status() != 303)
                .allMatch(o -> o.body().contains("samehandle_2"))
                .allMatch(o -> o.status() == 409 ? o.body().contains("방금 다른 분이 이 주소를 사용했어요. samehandle_2는 어떠세요?")
                        : o.status() == 400 && o.body().contains("이미 사용 중인 주소예요"));
        assertThat(outcomes).anyMatch(o -> o.status() == 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameNicknameIgnoringCaseGivesOneWinner() throws Exception {
        List<Outcome> outcomes = race(n -> signup("n" + n + "@x.com", "nick_" + n, n % 2 == 0 ? "RaceNick" : "racenick"));
        assertThat(outcomes).filteredOn(o -> o.status() == 303).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.status() != 303)
                .allMatch(o -> o.status() == 409 ? o.body().contains("방금 다른 분이 이 닉네임을 사용했어요")
                        : o.status() == 400 && o.body().contains("이미 사용 중인 닉네임이에요"));
        assertThat(outcomes).anyMatch(o -> o.status() == 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
    }
}

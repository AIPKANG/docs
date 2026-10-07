package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.MemberUniqueViolationTranslator;
import com.team.blog.account.application.MemberUniqueViolationTranslator.SignupContext;
import com.team.blog.account.domain.Handle;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Nickname;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.HandleTakenException;
import com.team.blog.support.IntegrationTestBase;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 같은 주소로 20건 동시 가입 → 1건만 성공, 나머지는 번역기에서 대안 제안(SC-003, FR-010). */
class HandleConcurrencyIT extends IntegrationTestBase {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    MemberUniqueViolationTranslator translator;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyOneOfTwentyConcurrentSignupsGetsTheHandle() throws Exception {
        Handle handle = Handle.parse("kim755030");
        int threads = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String nickname = "kim" + (char) ('a' + i);
                tasks.add(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        transactionTemplate.executeWithoutResult(status ->
                                memberRepository.saveAndFlush(new Member(handle, Nickname.of(nickname), Instant.now())));
                        return "OK";
                    } catch (DataIntegrityViolationException e) {
                        // 트랜잭션 경계 밖에서 번역한다
                        try {
                            translator.translate(e, SignupContext.email(handle));
                            return "UNEXPECTED";
                        } catch (HandleTakenException taken) {
                            return "TAKEN:" + taken.getSuggestion();
                        }
                    }
                });
            }
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(pool.submit(task));
            }
            ready.await();
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get());
            }
            assertThat(results).filteredOn("OK"::equals).hasSize(1);
            assertThat(results).filteredOn(r -> r.startsWith("TAKEN:")).hasSize(19)
                    .allMatch(r -> r.equals("TAKEN:kim755030_2"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE handle = 'kim755030'", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}

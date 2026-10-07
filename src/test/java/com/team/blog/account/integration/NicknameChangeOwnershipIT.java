package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.MemberUniqueViolationTranslator;
import com.team.blog.account.application.NicknameChangeService;
import com.team.blog.account.domain.NicknameViolation;
import com.team.blog.shared.error.LoginRequiredException;
import com.team.blog.shared.error.NicknameChangeTooSoonException;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

/** 본인만 변경·동시성(헌법 III·VI). */
class NicknameChangeOwnershipIT extends IntegrationTestBase {

    @Autowired
    NicknameChangeService nicknameChangeService;

    @Autowired
    MemberUniqueViolationTranslator translator;

    @Autowired
    AccountGuard accountGuard;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void changesOnlyTheGivenMember() {
        long a = members.active("kim755030", "김민서");
        long b = members.active("lee755030", "이영희");
        nicknameChangeService.change(a, "민서");
        assertThat(nickname(a)).isEqualTo("민서");
        assertThat(nickname(b)).isEqualTo("이영희");
        assertThat(jdbc.queryForObject("SELECT nickname_changed_at FROM member WHERE id = ?", Object.class, b)).isNull();
    }

    @Test
    void guestCannotChange() {
        assertThatThrownBy(() -> accountGuard.requireLoggedIn(Optional.empty())).isInstanceOf(LoginRequiredException.class);
    }

    @Test
    void twoConcurrentChangesOfTheSameMemberOnlyOneWins() throws Exception {
        long id = members.active("kim755030", "김민서");
        List<String> results = runConcurrently(
                () -> outcome(() -> nicknameChangeService.change(id, "민서하나")),
                () -> outcome(() -> nicknameChangeService.change(id, "민서둘")));
        assertThat(results).containsExactlyInAnyOrder("CHANGED", "TOO_SOON");
    }

    @Test
    void twoMembersRacingForTheSameNicknameOnlyOneWins() throws Exception {
        long a = members.active("kim755030", "김민서");
        long b = members.active("lee755030", "이영희");
        CountDownLatch aFlushed = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // A: 변경을 flush한 뒤 커밋 전에 멈춘다
            Future<String> first = pool.submit(() -> outcome(() -> transactionTemplate.executeWithoutResult(status -> {
                nicknameChangeService.change(a, "Park");
                aFlushed.countDown();
                await(releaseA);
            })));
            await(aFlushed);
            // B: A가 커밋하기 전이라 사전 검사는 통과하고, UNIQUE 인덱스에서 A를 기다린다
            Future<String> second = pool.submit(() -> outcome(() -> nicknameChangeService.change(b, "park")));
            long deadline = System.currentTimeMillis() + 10_000;
            while (jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Integer.class) == 0) {
                assertThat(System.currentTimeMillis()).isLessThan(deadline);
                Thread.onSpinWait();
            }
            releaseA.countDown();
            assertThat(first.get()).isEqualTo("CHANGED");
            assertThat(second.get()).isEqualTo("DUPLICATE_CONCURRENT");
        } finally {
            pool.shutdownNow();
        }
        assertThat(nickname(a)).isEqualTo("Park");
        assertThat(nickname(b)).isEqualTo("이영희");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String outcome(Runnable change) {
        try {
            change.run();
            return "CHANGED";
        } catch (NicknameChangeTooSoonException e) {
            return "TOO_SOON";
        } catch (NicknameViolationException e) {
            return "VIOLATION:" + e.getCode();
        } catch (DataIntegrityViolationException e) {
            // 트랜잭션 밖에서 번역
            try {
                translator.translate(e);
                return "UNEXPECTED";
            } catch (NicknameViolationException v) {
                return v.getCode() == NicknameViolation.NICKNAME_DUPLICATE && v.isConcurrent()
                        ? "DUPLICATE_CONCURRENT" : "OTHER";
            }
        }
    }

    @SafeVarargs
    private List<String> runConcurrently(Callable<String>... tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String nickname(long id) {
        return jdbc.queryForObject("SELECT nickname FROM member WHERE id = ?", String.class, id);
    }
}

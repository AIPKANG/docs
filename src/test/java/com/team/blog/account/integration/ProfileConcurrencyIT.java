package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.ProfileService;
import com.team.blog.account.application.ProfileUpdateCommand;
import com.team.blog.shared.error.ProfileValidationException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 003 T224: 동시 저장(FR-007)과 닉네임 경합. */
class ProfileConcurrencyIT extends IntegrationTestBase {

    @Autowired
    ProfileService profileService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentSavesOfSameMemberAreSerializedAndLastWins() throws Exception {
        long id = members.active("kim755030", "김민서");
        CurrentUser user = new CurrentUser(id, "USER");
        List<String> results = runConcurrently(
                () -> save(user, ProfileUpdateCommand.builder().bio("첫 번째").build()),
                () -> save(user, ProfileUpdateCommand.builder().bio("두 번째").build()));
        assertThat(results).containsOnly("OK");
        assertThat(jdbc.queryForObject("SELECT bio FROM member WHERE id = ?", String.class, id)).isIn("첫 번째", "두 번째");
    }

    @Test
    void sameNewNicknameForTwoMembersOnlyOneWins() throws Exception {
        long a = members.active("kim755030", "김민서");
        long b = members.active("lee755030", "이서준");
        List<String> results = runConcurrently(
                () -> save(new CurrentUser(a, "USER"), ProfileUpdateCommand.builder().nickname("하늘").build()),
                () -> save(new CurrentUser(b, "USER"), ProfileUpdateCommand.builder().nickname("하늘").build()));
        assertThat(results).filteredOn("OK"::equals).hasSize(1);
        assertThat(results).filteredOn(r -> r.startsWith("NICKNAME_DUPLICATE")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE nickname = '하늘'", Integer.class)).isEqualTo(1);
    }

    private String save(CurrentUser user, ProfileUpdateCommand command) {
        try {
            profileService.update(java.util.Optional.of(user), command);
            return "OK";
        } catch (ProfileValidationException e) {
            return e.getErrors().getFirst().code() + (e.isConflictOnly() ? ":409" : ":400");
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
            for (Future<String> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}

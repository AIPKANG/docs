package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.manualSave;
import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.AutosaveBuffer;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
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

/** 004 T325: 같은 출발 버전으로 동시에 온 저장 20건 중 성공은 정확히 1건(FR-016, SC-006, US3-8). */
class ConcurrentAutosaveIT extends IntegrationTestBase {

    @Autowired
    AutosaveBuffer buffer;

    private <T> List<T> runConcurrently(int n, java.util.function.IntFunction<Callable<T>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<T> c = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return c.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void bufferAcceptsExactlyOneOfTwentyWithNoExistingKey() throws Exception {
        long me = writer(members, "concurbuf");
        long postId = posts.draft(me, "", "", 0);
        List<AutosaveBuffer.SaveOutcome> outcomes = runConcurrently(20,
                i -> () -> buffer.save(postId, me, 0, 0, "탭" + i, "", Instant.now()));
        assertThat(outcomes).filteredOn(o -> o.kind() == AutosaveBuffer.SaveOutcome.Kind.ACCEPTED).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.kind() == AutosaveBuffer.SaveOutcome.Kind.CONFLICT).hasSize(19)
                .allMatch(o -> o.version() == 1);
        assertThat(posts.buffer(postId)).containsEntry("version", "1");
    }

    @Test
    void twentyConcurrentSavesOverHttpYieldOneSuccess() throws Exception {
        long me = writer(members, "concurhttp");
        long postId = posts.draft(me, "", "", 3);
        List<Integer> statuses = runConcurrently(20, i -> () -> mockMvc
                .perform(manualSave(postId, "탭" + i, "본문" + i, 3).with(TestAuth.member(me)))
                .andReturn().getResponse().getStatus());
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(19);
        var row = posts.post(postId);
        assertThat(row.get("edit_version")).isEqualTo(4L);
        String winner = (String) row.get("title");
        assertThat(row.get("content_md")).isEqualTo("본문" + winner.substring(1));
    }
}

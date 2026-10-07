package com.team.blog.post.integration;

import static com.team.blog.post.integration.PostTestSupport.writer;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.AutosaveBuffer;
import com.team.blog.post.application.EmptyDraftCleanupJob;
import com.team.blog.post.application.EmptyDraftCleanupService;
import com.team.blog.post.application.JobLock;
import com.team.blog.post.application.PostProperties;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 004 T331: 빈 임시글 정리(FR-025, SC-009, US5). */
class EmptyDraftCleanupIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-10-08T19:40:00Z");

    @Autowired
    EmptyDraftCleanupService cleanup;

    @Autowired
    AutosaveBuffer buffer;

    @Autowired
    JobLock jobLock;

    @Autowired
    PostProperties properties;

    @Autowired
    StringRedisTemplate redis;

    private long draftAged(long author, String title, String content, Duration createdAgo, Duration updatedAgo) {
        long id = posts.draft(author, title, content, 0);
        posts.setTimes(id, NOW.minus(createdAgo), NOW.minus(updatedAgo));
        return id;
    }

    @Test
    void deletesOnlyDraftsMatchingEveryCondition() {
        clock.set(NOW);
        long me = writer(members, "cleaner");
        Duration day = Duration.ofHours(24).plusMinutes(1);
        long empty = draftAged(me, "", "", day, day);
        long blankSpaces = draftAged(me, "  ", "\n\t", day, day);
        long titled = draftAged(me, "제목만", "", day, day);
        long bodied = draftAged(me, "", "본문만", day, day);
        long young = draftAged(me, "", "", Duration.ofHours(23), Duration.ofHours(23));
        long recentlyEdited = draftAged(me, "", "", Duration.ofDays(3), Duration.ofHours(2));
        long buffered = draftAged(me, "", "", day, day);
        buffer.save(buffered, me, 0, 0, "쓰는 중", "", NOW);
        long trashed = draftAged(me, "", "", day, day);
        posts.trash(trashed);
        long published = posts.published(me, "발행", "", 1, NOW.minus(Duration.ofDays(2)));
        posts.setTimes(published, NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(2)));

        assertThat(cleanup.runOnce()).isEqualTo(2);
        assertThat(posts.exists(empty)).isFalse();
        assertThat(posts.exists(blankSpaces)).isFalse();
        for (long kept : new long[] {titled, bodied, young, recentlyEdited, buffered, trashed, published}) {
            assertThat(posts.exists(kept)).as("post %d", kept).isTrue();
        }
        // 다음 실행에는 지울 것이 없다
        assertThat(cleanup.runOnce()).isZero();
    }

    @Test
    void jobSkipsWhileAnotherServerHoldsLock() {
        clock.set(NOW);
        long me = writer(members, "cleanlock");
        long empty = draftAged(me, "", "", Duration.ofDays(2), Duration.ofDays(2));
        EmptyDraftCleanupJob job = new EmptyDraftCleanupJob(cleanup, jobLock, properties);
        redis.opsForValue().set("post:empty-draft-cleanup-lock", "other", Duration.ofMinutes(10));
        job.run();
        assertThat(posts.exists(empty)).isTrue();
        redis.delete("post:empty-draft-cleanup-lock");
        job.run();
        assertThat(posts.exists(empty)).isFalse();
    }
}

package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.PostEditRow;
import com.team.blog.post.domain.PostStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 004 T333: DB 쪽 현재 버전·내용 규칙(research R-2). */
class PostVersionTest {

    private static final Instant T = Instant.parse("2026-10-07T00:00:00Z");

    @Test
    void draftUsesPostVersion() {
        PostEditRow row = new PostEditRow(1, 7, PostStatus.DRAFT, "t", "c", 4, T, null, null, null, null);
        assertThat(row.dbVersion()).isEqualTo(4);
        assertThat(row.dbContent().editing()).isFalse();
    }

    @Test
    void publishedUsesWorkingCopyOnlyWhenNewer() {
        PostEditRow withCopy = new PostEditRow(1, 7, PostStatus.PUBLISHED, "발행", "c", 4, T, 6L, "작업본", "w", T);
        assertThat(withCopy.dbVersion()).isEqualTo(6);
        assertThat(withCopy.dbContent().title()).isEqualTo("작업본");
        assertThat(withCopy.dbContent().editing()).isTrue();

        PostEditRow staleCopy = new PostEditRow(1, 7, PostStatus.PUBLISHED, "발행", "c", 8, T, 6L, "옛 작업본", "w", T);
        assertThat(staleCopy.dbVersion()).isEqualTo(8);
        assertThat(staleCopy.dbContent().title()).isEqualTo("발행");
        assertThat(staleCopy.hasWorkingCopy()).isFalse();
    }
}

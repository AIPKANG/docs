package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.application.FeedCursor;
import com.team.blog.shared.error.PostContentException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 009 T901: 이어 보기 기준 값(FR-004, FR-007). */
class FeedCursorTest {

    @Test
    void roundTripsWithMicroseconds() {
        FeedCursor cursor = new FeedCursor(Instant.parse("2026-10-02T14:03:12.123456Z"), 42);
        String encoded = cursor.encode();
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(FeedCursor.decode(encoded)).contains(cursor);
        assertThat(FeedCursor.decode(null)).isEmpty();
        assertThat(FeedCursor.decode("")).isEmpty();
    }

    @Test
    void garbageIsInvalid() {
        for (String bad : new String[] {"!!!", "abc", enc("x_1"), enc("1_x"), enc("-5_1"), enc("5_0"), "A".repeat(100)}) {
            assertThatThrownBy(() -> FeedCursor.decode(bad)).isInstanceOf(PostContentException.class)
                    .extracting("code").isEqualTo("INVALID_CURSOR");
        }
    }

    private static String enc(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.US_ASCII));
    }
}

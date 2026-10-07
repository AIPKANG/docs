package com.team.blog.post.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.shared.error.ContentTooComplexException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 007 T410·SC-004: 중첩 20단계·1초 제한. */
class ContentRendererLimitsTest {

    private static final ContentRenderer RENDERER = TestRenderers.create();

    @Test
    void quoteAndListNestingOverTwentyIsRejected() {
        assertThatThrownBy(() -> RENDERER.render(">".repeat(25) + " 깊음")).isInstanceOf(ContentTooComplexException.class);
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            list.append("  ".repeat(i)).append("- ").append(i).append('\n');
        }
        assertThatThrownBy(() -> RENDERER.render(list.toString())).isInstanceOf(ContentTooComplexException.class);
    }

    @Test
    void fifteenLevelsAreAllowed() {
        assertThat(RENDERER.render(">".repeat(15) + " 괜찮음").html()).contains("괜찮음");
        assertThat(RENDERER.render(">".repeat(20) + " 경계").html()).contains("경계");
    }

    @Test
    void hundredThousandCharactersRenderWithinOneSecond() {
        StringBuilder md = new StringBuilder();
        while (md.length() < 100_000) {
            md.append("## 제목 ").append(md.length()).append("\n\n**굵게** 문단 [링크](https://a.b) `코드`\n\n- 목록\n\n");
        }
        long start = System.nanoTime();
        assertThat(RENDERER.render(md.substring(0, 100_000)).html()).isNotEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void timeoutIsRejected() {
        ContentRenderer tiny = TestRenderers.create(20, Duration.ofNanos(1));
        assertThatThrownBy(() -> tiny.render("# 제목\n\n본문".repeat(2000))).isInstanceOf(ContentTooComplexException.class);
    }
}

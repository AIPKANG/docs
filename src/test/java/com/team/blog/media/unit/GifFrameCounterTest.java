package com.team.blog.media.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.domain.GifFrameCounter;
import com.team.blog.support.TestImages;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 008 T802: GIF 프레임 수(FR-031). */
class GifFrameCounterTest {

    @Test
    void countsFrames() {
        assertThat(GifFrameCounter.count(TestImages.gif(10, 10, 1), 300)).hasValue(1);
        assertThat(GifFrameCounter.count(TestImages.gif(10, 10, 5), 300)).hasValue(5);
    }

    @Test
    void stopsPastLimit() {
        assertThat(GifFrameCounter.count(TestImages.gif(4, 4, 301), 300)).hasValue(301);
    }

    @Test
    void brokenOrTruncatedIsEmpty() {
        byte[] gif = TestImages.gif(10, 10, 3);
        assertThat(GifFrameCounter.count(Arrays.copyOf(gif, gif.length - 5), 300)).isEmpty();
        assertThat(GifFrameCounter.count(new byte[] {1, 2, 3}, 300)).isEmpty();
        assertThat(GifFrameCounter.count(TestImages.png(10, 10), 300)).isEmpty();
    }
}

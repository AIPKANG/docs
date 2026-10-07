package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.ProfileAvatar;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 003 T238: 기본 아이콘(11 §4-3, FR-018). */
class ProfileAvatarTest {

    @Test
    void initialIsFirstLetterUppercased() {
        assertThat(new ProfileAvatar("kim755030", "김민서", null).initial()).isEqualTo("김");
        assertThat(new ProfileAvatar("kim755030", "kim", null).initial()).isEqualTo("K");
        assertThat(new ProfileAvatar("kim755030", "1abc", null).initial()).isEqualTo("1");
        assertThat(new ProfileAvatar("kim755030", null, null).initial()).isEqualTo("?");
    }

    @Test
    void colorIsStablePerHandleAndSpreadsAcrossPalette() {
        ProfileAvatar a = new ProfileAvatar("kim755030", "김민서", null);
        assertThat(a.colorIndex()).isEqualTo(new ProfileAvatar("kim755030", "다른닉", null).colorIndex());
        assertThat(a.colorIndex()).isBetween(0, 7);
        // Java String.hashCode 명세로 고정된 값(실행마다 같음)
        assertThat(a.colorIndex()).isEqualTo(Math.floorMod("kim755030".hashCode(), 8));
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(new ProfileAvatar("user_" + i, "닉", null).colorIndex());
        }
        assertThat(seen).hasSize(8);
    }

    @Test
    void everyPaletteColorHasAtLeast45ContrastWithWhiteText() {
        for (String color : ProfileAvatar.PALETTE) {
            assertThat(contrastWithWhite(color)).as(color).isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test
    void imagePresence() {
        assertThat(new ProfileAvatar("a", "b", "http://x/y.webp").hasImage()).isTrue();
        assertThat(new ProfileAvatar("a", "b", null).hasImage()).isFalse();
    }

    private static double contrastWithWhite(String hex) {
        double r = channel(Integer.parseInt(hex.substring(1, 3), 16));
        double g = channel(Integer.parseInt(hex.substring(3, 5), 16));
        double b = channel(Integer.parseInt(hex.substring(5, 7), 16));
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return 1.05 / (luminance + 0.05);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}

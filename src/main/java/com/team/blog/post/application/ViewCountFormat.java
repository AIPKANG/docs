package com.team.blog.post.application;

import java.text.NumberFormat;
import java.util.Locale;

/** 조회 수 표시(40 §2, 010 FR-012): 1만 미만 {@code 1,234}, 이상 {@code 1.2만}(소수 첫째 자리 내림, .0은 생략). */
public final class ViewCountFormat {

    private ViewCountFormat() {
    }

    public static String format(long count) {
        if (count < 10_000) {
            return NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.max(0, count));
        }
        long tenths = count / 1_000; // 만 단위 × 10
        long whole = tenths / 10;
        long fraction = tenths % 10;
        String number = NumberFormat.getIntegerInstance(Locale.KOREA).format(whole);
        return fraction == 0 ? number + "만" : number + "." + fraction + "만";
    }
}

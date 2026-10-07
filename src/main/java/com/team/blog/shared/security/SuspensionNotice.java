package com.team.blog.shared.security;

import com.team.blog.shared.web.KoreanDateFormatter;
import java.time.Instant;

/** 정지 안내 문구 "정지된 계정이에요 (~기한, 사유)"(FR-030). */
public final class SuspensionNotice {

    private SuspensionNotice() {
    }

    public static String text(Instant endsAt, String reason) {
        String until = endsAt == null ? "영구" : KoreanDateFormatter.dateTime(endsAt) + "까지";
        return "정지된 계정이에요 (~" + until + ", " + (reason == null ? "" : reason) + ")";
    }
}

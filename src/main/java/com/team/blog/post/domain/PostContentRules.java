package com.team.blog.post.domain;

import java.util.Optional;

/**
 * 저장 전 제목·본문 규칙(research R-7). 발행 전 저장은 원문을 남긴다 — 정화·렌더링은 발행 때(005·007).
 * 제목은 한 줄 칸이라 줄바꿈·제어 문자만 공백으로 바꾼다.
 */
public final class PostContentRules {

    public static final String TITLE_TOO_LONG = "TITLE_TOO_LONG";
    public static final String CONTENT_TOO_LONG = "CONTENT_TOO_LONG";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    private PostContentRules() {
    }

    /** {@code null}은 빈 제목. 줄바꿈·탭·C0/C1 제어 문자 → 공백. */
    public static String normalizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        raw.codePoints().forEach(cp -> {
            if (Character.getType(cp) == Character.CONTROL) {
                out.append(' ');
            } else {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    /** {@code null}은 빈 본문. 줄바꿈 {@code \r\n}·{@code \r}은 {@code \n}으로. */
    public static String normalizeContent(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 첫 위반 코드. 길이는 코드 포인트 수(DB {@code char_length}와 같은 기준). */
    public static Optional<String> firstViolation(String title, String content, int titleMax, int contentMax) {
        if (title.codePointCount(0, title.length()) > titleMax) {
            return Optional.of(TITLE_TOO_LONG);
        }
        if (content.codePointCount(0, content.length()) > contentMax) {
            return Optional.of(CONTENT_TOO_LONG);
        }
        return Optional.empty();
    }

    /** 제목·본문이 모두 공백뿐인지(빈 임시글 정리, FR-025). */
    public static boolean isBlank(String title, String content) {
        return (title == null || title.isBlank()) && (content == null || content.isBlank());
    }
}

package com.team.blog.interaction.application;

import java.text.Normalizer;
import java.util.Optional;

/**
 * 댓글 내용 정리(21 §4, 014 FR-008·FR-009): ① NFC ② 보이지 않는 글자·방향 제어 문자·제어 문자 제거(줄바꿈은 유지)
 * ③ 줄바꿈 {@code \n}으로 ④ 앞뒤 공백 제거 ⑤ 빈 줄 연속은 하나로. 1~1000자(코드 포인트). 금칙어는 적용하지 않는다.
 */
public final class CommentContentRules {

    public static final String REQUIRED = "COMMENT_REQUIRED";
    public static final String TOO_LONG = "COMMENT_TOO_LONG";

    private CommentContentRules() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().filter(cp -> cp == '\n' || !removed(cp)).forEach(out::appendCodePoint);
        String trimmed = out.toString().strip();
        // 공백만 있는 줄은 빈 줄로, 빈 줄이 여러 개 이어지면 하나로
        return trimmed.replaceAll("(?m)^[ \\t\\u3000]+$", "").replaceAll("\n{3,}", "\n\n");
    }

    public static Optional<String> violation(String cleaned, int maxLength) {
        if (cleaned.isEmpty()) {
            return Optional.of(REQUIRED);
        }
        if (cleaned.codePointCount(0, cleaned.length()) > maxLength) {
            return Optional.of(TOO_LONG);
        }
        return Optional.empty();
    }

    private static boolean removed(int cp) {
        return (cp >= 0x200B && cp <= 0x200F) || (cp >= 0x2060 && cp <= 0x2069) || cp == 0xFEFF
                || (cp >= 0x202A && cp <= 0x202E) || Character.getType(cp) == Character.CONTROL;
    }
}

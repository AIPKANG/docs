package com.team.blog.post.domain;

import java.text.Normalizer;

/**
 * 글 제목 정리(12 §7-4, 007 FR-018): NFC → 보이지 않는 글자(U+200B~U+200F, U+2060~U+2069, U+FEFF)·방향 제어 문자
 * (U+202A~U+202E)·제어 문자 제거 → 앞뒤 공백 제거. 길이(1~100자) 검사는 발행 검증(005)이 한다. 화면은 글자로만 표시한다.
 */
public final class PostTitleRules {

    private PostTitleRules() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(nfc.length());
        nfc.codePoints().filter(cp -> !removed(cp)).forEach(out::appendCodePoint);
        return out.toString().strip();
    }

    static boolean removed(int cp) {
        return (cp >= 0x200B && cp <= 0x200F) || (cp >= 0x2060 && cp <= 0x2069) || cp == 0xFEFF
                || (cp >= 0x202A && cp <= 0x202E) || Character.getType(cp) == Character.CONTROL;
    }
}

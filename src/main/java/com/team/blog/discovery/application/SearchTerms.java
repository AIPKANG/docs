package com.team.blog.discovery.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * 검색어 정리(020 FR-003·FR-004, 33 §2): NFC → 앞뒤 공백 제거 → 50자 → 띄어쓰기로 나눔 → 1글자 무시 → 최대 5단어.
 * 2글자 단어는 제목·태그에서만, 3글자 이상은 본문까지 찾는다.
 */
public record SearchTerms(String text, List<String> words) {

    public static final int MAX_LENGTH = 50;
    public static final int MAX_WORDS = 5;

    public static SearchTerms parse(String raw) {
        String text = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_LENGTH)).strip();
        }
        List<String> words = new ArrayList<>();
        for (String w : text.split("\\s+")) {
            if (w.codePointCount(0, w.length()) >= 2 && !words.contains(w) && words.size() < MAX_WORDS) {
                words.add(w);
            }
        }
        return new SearchTerms(text, List.copyOf(words));
    }

    public boolean empty() {
        return words.isEmpty();
    }

    /** 2글자 단어가 있어 본문은 찾지 않은 단어가 있음(안내 문구). */
    public boolean hasTwoCharWord() {
        return words.stream().anyMatch(w -> !searchesBody(w));
    }

    public static boolean searchesBody(String word) {
        return word.codePointCount(0, word.length()) >= 3;
    }

    /** {@code ILIKE … ESCAPE '\'}용 부분 일치 패턴. {@code % _ \}는 글자 그대로(FR-007). */
    public static String likePattern(String word) {
        return "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}

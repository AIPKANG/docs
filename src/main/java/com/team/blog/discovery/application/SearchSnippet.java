package com.team.blog.discovery.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * 검색 결과 미리보기(020 FR-014·FR-015). 본문(없으면 제목)에서 검색어가 처음 나온 곳의 앞뒤 40자, 잘리면 {@code …}.
 * 강조는 반드시 "문장 전체 HTML 이스케이프 → 이스케이프된 검색어에 {@code <mark>}" 순서다. 스크립트용으로는 같은 결과를
 * 조각 목록(글자, 강조 여부)으로도 준다.
 */
public final class SearchSnippet {

    public record Part(String text, boolean mark) {
    }

    public record Result(String html, List<Part> parts) {
    }

    private static final int AROUND = 40;

    private SearchSnippet() {
    }

    public static Result of(String body, String title, List<String> words) {
        String flatBody = flat(body);
        String source = flatBody;
        int at = firstIndex(flatBody, words);
        if (at < 0) {
            source = flat(title);
            at = Math.max(0, firstIndex(source, words));
        }
        int startCp = Math.max(0, source.codePointCount(0, at) - AROUND);
        int total = source.codePointCount(0, source.length());
        int matchLen = 0;
        String lower = source.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (lower.startsWith(w.toLowerCase(Locale.ROOT), at)) {
                matchLen = Math.max(matchLen, w.codePointCount(0, w.length()));
            }
        }
        int endCp = Math.min(total, source.codePointCount(0, at) + matchLen + AROUND);
        String text = source.substring(source.offsetByCodePoints(0, startCp), source.offsetByCodePoints(0, endCp));
        String prefix = startCp > 0 ? "…" : "";
        String suffix = endCp < total ? "…" : "";
        List<Part> parts = new ArrayList<>();
        StringBuilder html = new StringBuilder(prefix);
        if (!prefix.isEmpty()) {
            parts.add(new Part(prefix, false));
        }
        Pattern pattern = wordsPattern(words);
        Matcher m = pattern.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                parts.add(new Part(text.substring(last, m.start()), false));
            }
            parts.add(new Part(m.group(), true));
            last = m.end();
        }
        if (last < text.length()) {
            parts.add(new Part(text.substring(last), false));
        }
        // HTML: 문장 전체를 이스케이프한 뒤, 이스케이프된 검색어에 <mark>
        String escaped = HtmlUtils.htmlEscape(text);
        html.append(wordsPattern(words.stream().map(HtmlUtils::htmlEscape).toList()).matcher(escaped)
                .replaceAll(r -> "<mark>" + Matcher.quoteReplacement(r.group()) + "</mark>"));
        html.append(suffix);
        if (!suffix.isEmpty()) {
            parts.add(new Part(suffix, false));
        }
        return new Result(html.toString(), parts);
    }

    private static Pattern wordsPattern(List<String> words) {
        String alternation = words.stream().sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote).reduce((a, b) -> a + "|" + b).orElse("(?!)");
        return Pattern.compile(alternation, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static int firstIndex(String text, List<String> words) {
        String lower = text.toLowerCase(Locale.ROOT);
        int best = -1;
        for (String w : words) {
            int i = lower.indexOf(w.toLowerCase(Locale.ROOT));
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    private static String flat(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip();
    }
}

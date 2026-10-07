package com.team.blog.post.markdown;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 본문 제목 앵커(12 §3, §7-3): {@code h-} + 제목 글자. 글자·숫자·{@code _}·{@code -}만, 공백은 {@code -}, 영문 소문자,
 * 100자까지. 같은 이름은 {@code -1}, {@code -2}… 렌더링 한 번마다 새로 만든다.
 */
final class HeadingAnchors {

    static final String PREFIX = "h-";
    private static final int MAX = 100;
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N}_-]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final Map<String, Integer> used = new HashMap<>();

    String next(String headingText) {
        String base = SPACES.matcher(headingText.strip()).replaceAll("-");
        base = NOT_ALLOWED.matcher(base).replaceAll("").toLowerCase(Locale.ROOT);
        if (base.isEmpty()) {
            base = "section";
        }
        base = cut(base, MAX);
        Integer count = used.get(base);
        if (count == null) {
            used.put(base, 0);
            return PREFIX + base;
        }
        while (true) {
            count++;
            String suffix = "-" + count;
            String candidate = cut(base, MAX - suffix.length()) + suffix;
            if (!used.containsKey(candidate)) {
                used.put(base, count);
                used.put(candidate, 0);
                return PREFIX + candidate;
            }
        }
    }

    private static String cut(String value, int max) {
        if (value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max));
    }
}

package com.team.blog.post.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 위험 HTML 검사기(12 §9 검사 방법). 결과 HTML에서 실제 태그만 꺼내
 * ① 태그 이름이 허용 목록에 있는지 ② 속성 이름에 {@code on…}·{@code style}이 없는지 ③ {@code href}·{@code src} 값을 브라우저처럼
 * 엔티티를 풀고 공백·제어 문자를 지운 뒤 {@code javascript:}·{@code vbscript:}·{@code data:}로 시작하지 않는지 본다.
 */
final class DangerousHtmlChecker {

    static final Set<String> ALLOWED = Set.of("p", "br", "hr", "blockquote", "h2", "h3", "h4", "h5", "h6", "strong",
            "em", "del", "ul", "ol", "li", "input", "code", "pre", "table", "thead", "tbody", "tr", "th", "td", "a", "img");

    private static final Pattern TAG = Pattern.compile("<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9-]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern ATTR = Pattern.compile("([^\\s=/\"'>]+)\\s*(?:=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+))?");
    private static final Pattern NUM_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);?");

    private DangerousHtmlChecker() {
    }

    /** 문제 목록(비었으면 안전). */
    static List<String> problems(String html) {
        List<String> problems = new ArrayList<>();
        Matcher tag = TAG.matcher(html);
        while (tag.find()) {
            String name = tag.group(2).toLowerCase(Locale.ROOT);
            if (!ALLOWED.contains(name)) {
                problems.add("tag:" + name);
            }
            Matcher attr = ATTR.matcher(tag.group(3));
            while (attr.find()) {
                String attrName = attr.group(1).toLowerCase(Locale.ROOT);
                if (attrName.startsWith("on") || attrName.equals("style")) {
                    problems.add("attr:" + attrName);
                }
                if ((attrName.equals("href") || attrName.equals("src")) && attr.group(2) != null) {
                    String value = unquote(attr.group(2));
                    String normalized = decode(value).replaceAll("[\\s\\p{Cntrl}]", "").toLowerCase(Locale.ROOT);
                    if (normalized.startsWith("javascript:") || normalized.startsWith("vbscript:")
                            || normalized.startsWith("data:")) {
                        problems.add("url:" + value);
                    }
                }
            }
        }
        return problems;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && (v.startsWith("\"") || v.startsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    static String decode(String value) {
        String v = value.replace("&colon;", ":").replace("&Tab;", "\t").replace("&NewLine;", "\n")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">");
        Matcher m = NUM_ENTITY.matcher(v);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            m.appendReplacement(out, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        m.appendTail(out);
        return out.toString();
    }
}

package com.team.blog.tag.application.suggest;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI에 보내기 전 정리(021 FR-012, 34 §4). Markdown 기호(제목·강조·목록·인용) 제거, 이미지 문법 전체 제거, 링크는 글자만,
 * 코드 블록은 언어 이름과 앞 5줄만, 연속 공백·줄바꿈 하나로, NFC. 대소문자는 바꾸지 않는다. 같은 내용이면 같은 결과가 되어 캐시 키가 된다.
 */
public final class AiInputCleaner {

    private static final Pattern FENCE = Pattern.compile("^(```|~~~)\\s*([\\w+#.-]*)[^\\n]*\\n(.*?)(?:^\\1[^\\n]*$|\\z)",
            Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern AUTOLINK = Pattern.compile("<(https?://[^>\\s]+)>");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s*", Pattern.MULTILINE);
    private static final Pattern QUOTE = Pattern.compile("^\\s*(>\\s*)+", Pattern.MULTILINE);
    private static final Pattern LIST = Pattern.compile("^\\s*(?:[-*+]|\\d+[.)])\\s+(?:\\[[ xX]]\\s+)?", Pattern.MULTILINE);
    private static final Pattern EMPHASIS = Pattern.compile("(\\*{1,3}|_{2,3}|~~)(?=\\S)(.+?)(?<=\\S)\\1");
    private static final Pattern RULE = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$", Pattern.MULTILINE);
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private AiInputCleaner() {
    }

    public static String clean(String title, String markdown) {
        String body = markdown == null ? "" : markdown.replace("\r\n", "\n");
        // 코드 블록: 언어 + 앞 5줄. 다른 규칙이 코드 안을 건드리지 않게 자리표시로 빼 둔다
        List<String> codes = new ArrayList<>();
        Matcher fence = FENCE.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (fence.find()) {
            String lang = fence.group(2);
            String[] lines = fence.group(3).split("\n", -1);
            StringBuilder code = new StringBuilder(lang.isEmpty() ? "" : lang + " ");
            for (int i = 0; i < Math.min(5, lines.length); i++) {
                code.append(lines[i]).append('\n');
            }
            codes.add(code.toString());
            fence.appendReplacement(sb, Matcher.quoteReplacement("\u0000" + (codes.size() - 1) + "\u0000"));
        }
        fence.appendTail(sb);
        body = sb.toString();
        body = IMAGE.matcher(body).replaceAll("");
        body = LINK.matcher(body).replaceAll("$1");
        body = AUTOLINK.matcher(body).replaceAll("");
        body = RULE.matcher(body).replaceAll("");
        body = HEADING.matcher(body).replaceAll("");
        body = QUOTE.matcher(body).replaceAll("");
        body = LIST.matcher(body).replaceAll("");
        body = EMPHASIS.matcher(body).replaceAll("$2");
        for (int i = 0; i < codes.size(); i++) {
            body = body.replace("\u0000" + i + "\u0000", codes.get(i));
        }
        String text = ((title == null ? "" : title.strip()) + "\n" + body);
        return SPACES.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll(" ").strip();
    }

    /** 공급자별 최대 글자 수(코드 포인트)로 자른다. */
    public static String truncate(String text, int maxChars) {
        if (text.codePointCount(0, text.length()) <= maxChars) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxChars));
    }

    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}

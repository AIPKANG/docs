package com.team.blog.tag.domain;

import com.team.blog.shared.text.BannedWordFilter;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 태그 정규화(22 §2). 발행·자동완성·태그 주소·검색이 이 클래스 하나를 쓴다.
 * ① NFKC → ② 숨은 글자·방향 제어·제어 문자 제거 → ③ 앞뒤 공백 → ④ 맨 앞 {@code #} 제거 → ⑤ 소문자(ROOT) →
 * ⑥ 공백 묶음 → {@code -} → ⑦ {@code -} 연속·처음·끝 정리 → ⑧ 허용 문자·한글/영문/숫자 1자 이상·1~30자 → ⑨ 금칙어.
 */
@Component
public class TagNormalizer {

    public static final String INVALID_TAG = "INVALID_TAG";
    public static final String TAG_TOO_LONG = "TAG_TOO_LONG";
    public static final String TAG_BANNED_WORD = "TAG_BANNED_WORD";
    public static final int MAX_LENGTH = 30;

    public static final Pattern ALLOWED = Pattern.compile("[가-힣a-z0-9._+#-]+");
    public static final Pattern HAS_WORD = Pattern.compile("[가-힣a-z0-9]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern DASHES = Pattern.compile("-{2,}");

    private final BannedWordFilter bannedWordFilter;

    public TagNormalizer(BannedWordFilter bannedWordFilter) {
        this.bannedWordFilter = bannedWordFilter;
    }

    /** 정규화한 이름, 또는 {@link TagRejectedException}. */
    public String normalize(String raw) {
        String name = shape(raw);
        if (name.isEmpty() || !ALLOWED.matcher(name).matches() || !HAS_WORD.matcher(name).find()) {
            throw new TagRejectedException(INVALID_TAG);
        }
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            throw new TagRejectedException(TAG_TOO_LONG);
        }
        if (bannedWordFilter.containsBanned(name.replace('-', ' '))) {
            throw new TagRejectedException(TAG_BANNED_WORD);
        }
        return name;
    }

    /**
     * 주소·자동완성·필터용(013 FR-001·FR-017·FR-021): ①~⑧ 규칙으로 정리한 이름, 형식에 맞지 않으면 빈 값.
     * 금칙어 검사는 하지 않는다(찾기만 하므로).
     */
    public static java.util.Optional<String> lookupName(String raw) {
        String name = shape(raw);
        if (name.isEmpty() || !ALLOWED.matcher(name).matches() || !HAS_WORD.matcher(name).find()
                || name.codePointCount(0, name.length()) > MAX_LENGTH) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(name);
    }

    /** ①~⑦(검사 전 모양 만들기). 화면 미리 보여주기와 같은 규칙. */
    public static String shape(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        StringBuilder kept = new StringBuilder(s.length());
        s.codePoints().filter(cp -> !hidden(cp) || Character.isWhitespace(cp) && cp != '​').forEach(kept::appendCodePoint);
        s = kept.toString().strip();
        int i = 0;
        while (i < s.length() && s.charAt(i) == '#') {
            i++;
        }
        s = s.substring(i).strip().toLowerCase(Locale.ROOT);
        s = SPACES.matcher(s).replaceAll("-");
        s = DASHES.matcher(s).replaceAll("-");
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '-') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '-') {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean hidden(int cp) {
        return (cp >= 0x200B && cp <= 0x200F) || (cp >= 0x2060 && cp <= 0x2069) || cp == 0xFEFF
                || (cp >= 0x202A && cp <= 0x202E) || Character.getType(cp) == Character.CONTROL;
    }
}

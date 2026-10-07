package com.team.blog.shared.text;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 금칙어 필터(09 §4, research R-9). 닉네임·블로그 주소·소개(003)가 함께 쓴다.
 *
 * <p>검사: 소문자(NFC) → {@link TextVariants} 4변형 → 각 변형에서 예외 단어를 먼저 제거(긴 단어 우선, 겹치면 왼쪽)
 * → 남은 문자열의 부분 문자열이 금칙어 집합에 있으면 차단.
 *
 * <p>반환값은 {@code boolean}뿐이다. 걸린 단어는 응답·예외·로그 어디로도 나가지 않는다(SC-006).
 * 로그에는 "banned word matched"와 입력 길이만 남긴다.
 */
@Component
public class BannedWordFilter {

    private static final Logger log = LoggerFactory.getLogger(BannedWordFilter.class);

    /** 예외 단어를 지운 자리. 앞뒤 조각이 이어져 새 단어가 생기지 않도록 부분 문자열 검사에서 경계로 쓴다. */
    private static final char CUT = '\u0000';

    private final Set<String> banned;
    private final List<String> exceptionsLongestFirst;
    private final int maxBannedLength;

    @Autowired
    public BannedWordFilter(ResourceLoader resourceLoader,
                            @Value("${blog.text.banned-words.location:classpath:policy/banned-words.txt}") String location,
                            @Value("${blog.text.banned-words.exceptions-location:classpath:policy/banned-words-exceptions.txt}")
                            String exceptionsLocation) {
        this(WordListLoader.load(resourceLoader.getResource(location)),
                WordListLoader.load(resourceLoader.getResource(exceptionsLocation)));
    }

    private BannedWordFilter(Set<String> banned, Set<String> exceptions) {
        if (banned.isEmpty()) {
            throw new IllegalStateException("banned word list is empty");
        }
        this.banned = Set.copyOf(banned.stream().map(WordListLoader::normalize).toList());
        this.exceptionsLongestFirst = exceptions.stream()
                .map(WordListLoader::normalize)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        this.maxBannedLength = this.banned.stream().mapToInt(String::length).max().orElse(0);
    }

    /** 단위 테스트·도구용: 목록을 직접 준다. */
    public static BannedWordFilter of(Set<String> banned, Set<String> exceptions) {
        return new BannedWordFilter(banned, exceptions);
    }

    public boolean containsBanned(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = WordListLoader.normalize(text);
        for (String variant : TextVariants.of(lower)) {
            if (containsBannedSubstring(removeExceptions(variant))) {
                log.info("banned word matched (input length={})", text.length());
                return true;
            }
        }
        return false;
    }

    /** 블로그 주소 본문: 본문 그대로와 {@code _}를 지운 값 둘 다 검사한다(research R-9, U-6). */
    public boolean containsBannedInHandleBody(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return containsBanned(body) || containsBanned(body.replace("_", ""));
    }

    private String removeExceptions(String text) {
        if (exceptionsLongestFirst.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        outer:
        while (i < text.length()) {
            for (String exception : exceptionsLongestFirst) {
                if (text.startsWith(exception, i)) {
                    out.append(CUT);
                    i += exception.length();
                    continue outer;
                }
            }
            out.append(text.charAt(i));
            i++;
        }
        return out.toString();
    }

    private boolean containsBannedSubstring(String text) {
        int n = text.length();
        for (int start = 0; start < n; start++) {
            int limit = Math.min(n, start + maxBannedLength);
            for (int end = start + 1; end <= limit; end++) {
                if (text.charAt(end - 1) == CUT) {
                    break;
                }
                if (banned.contains(text.substring(start, end))) {
                    return true;
                }
            }
        }
        return false;
    }
}

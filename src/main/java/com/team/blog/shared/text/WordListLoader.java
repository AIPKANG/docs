package com.team.blog.shared.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.Resource;

/**
 * 단어 목록 파일 읽기: 한 줄 한 단어, {@code #} 주석·빈 줄 무시, NFC + 소문자 정규화, 불변 집합.
 * 파일이 없거나 단어가 하나도 없으면 예외를 던져 애플리케이션 <b>시작을 실패</b>시킨다(조용히 필터가 꺼지는 것을 막는다).
 */
public final class WordListLoader {

    private WordListLoader() {
    }

    public static Set<String> load(Resource resource) {
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("word list not found: " + (resource == null ? "null" : resource.getDescription()));
        }
        Set<String> words = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.strip();
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }
                words.add(normalize(word));
            }
        } catch (IOException e) {
            throw new IllegalStateException("word list unreadable: " + resource.getDescription(), e);
        }
        if (words.isEmpty()) {
            throw new IllegalStateException("word list is empty: " + resource.getDescription());
        }
        return Set.copyOf(words);
    }

    /** NFC + 소문자. 목록과 입력에 같은 정규화를 쓴다. */
    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}

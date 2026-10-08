package com.team.blog.tag.application.suggest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천 재사용(021 FR-022~FR-027, 34 §5): ① {@code ai:tag:v{버전}:{SHA-256(정리된 입력)}} 30일, 사용자와 무관하게 공유
 * ② {@code ai:tag:post:{글}} = 지난번 정리된 입력·추천·공급자, 7일, 3글자 Jaccard ≥ 0.9면 재사용. 키에 내용 자체는 없다.
 * 이미 붙인 태그·인기 태그는 판정에 넣지 않고, 저장은 태그를 빼기 전 결과로.
 */
@Component
public class AiSuggestionCache {

    public record Entry(List<String> tags, String provider, String input) {
    }

    private final StringRedisTemplate redis;
    private final AiTagProperties properties;
    private final JsonMapper jsonMapper;

    public AiSuggestionCache(StringRedisTemplate redis, AiTagProperties properties, JsonMapper jsonMapper) {
        this.redis = redis;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    private String exactKey(String input) {
        return "ai:tag:v" + properties.promptVersion() + ":" + sha256(input);
    }

    private String postKey(long postId) {
        return "ai:tag:v" + properties.promptVersion() + ":post:" + postId;
    }

    public Optional<Entry> exact(String input) {
        return read(exactKey(input));
    }

    public Optional<Entry> similar(long postId, String input) {
        return read(postKey(postId))
                .filter(e -> e.input() != null && TrigramSimilarity.jaccard(e.input(), input) >= properties.similarity());
    }

    public void put(long postId, String input, List<String> tags, String provider) {
        redis.opsForValue().set(exactKey(input), jsonMapper.writeValueAsString(new Entry(tags, provider, null)),
                properties.exactCacheTtl());
        redis.opsForValue().set(postKey(postId), jsonMapper.writeValueAsString(new Entry(tags, provider, input)),
                properties.similarCacheTtl());
    }

    private Optional<Entry> read(String key) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(raw, Entry.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

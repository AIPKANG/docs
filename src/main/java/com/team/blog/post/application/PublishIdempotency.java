package com.team.blog.post.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 발행 요청 식별자(05 §6, FR-019·FR-020). Redis {@code idem:publish:{memberId}:{key}}:
 * {@code P|hash}(처리 중) 또는 {@code D|hash|응답 JSON}(완료), {@code SET NX EX}로 처음만 처리한다.
 * Redis에 닿지 못하면 확인을 건너뛴다 — 행 잠금과 버전 확인이 두 번째 요청을 409로 막는다.
 */
@Component
public class PublishIdempotency {

    private static final Logger log = LoggerFactory.getLogger(PublishIdempotency.class);
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9-]{1,64}");

    public enum State { NEW, IN_PROGRESS, DONE, REUSED, UNAVAILABLE }

    public record Begin(State state, PublishResult response) {
    }

    private final StringRedisTemplate redis;
    private final PostProperties properties;
    private final JsonMapper jsonMapper;

    public PublishIdempotency(StringRedisTemplate redis, PostProperties properties, JsonMapper jsonMapper) {
        this.redis = redis;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public static boolean validKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    static String redisKey(long memberId, String key) {
        return "idem:publish:" + memberId + ":" + key;
    }

    public Begin begin(long memberId, String key, String hash) {
        String rk = redisKey(memberId, key);
        try {
            Boolean created = redis.opsForValue().setIfAbsent(rk, "P|" + hash, properties.publish().idempotencyTtl());
            if (Boolean.TRUE.equals(created)) {
                return new Begin(State.NEW, null);
            }
            String value = redis.opsForValue().get(rk);
            if (value == null) {
                // 그사이 만료·삭제됨: 한 번 더 시도
                created = redis.opsForValue().setIfAbsent(rk, "P|" + hash, properties.publish().idempotencyTtl());
                return new Begin(Boolean.TRUE.equals(created) ? State.NEW : State.IN_PROGRESS, null);
            }
            String[] parts = value.split("\\|", 3);
            if (parts.length < 2 || !parts[1].equals(hash)) {
                return new Begin(State.REUSED, null);
            }
            if ("D".equals(parts[0]) && parts.length == 3) {
                return new Begin(State.DONE, jsonMapper.readValue(parts[2], PublishResult.class));
            }
            return new Begin(State.IN_PROGRESS, null);
        } catch (DataAccessException e) {
            log.warn("publish idempotency store unavailable, relying on row lock and version check: {}", e.getMessage());
            return new Begin(State.UNAVAILABLE, null);
        }
    }

    public void complete(long memberId, String key, String hash, PublishResult result) {
        try {
            redis.opsForValue().set(redisKey(memberId, key), "D|" + hash + "|" + jsonMapper.writeValueAsString(result),
                    properties.publish().idempotencyTtl());
        } catch (DataAccessException e) {
            log.warn("publish idempotency result not stored: {}", e.getMessage());
        }
    }

    /** 처리 실패: 같은 식별자로 다시 시도할 수 있게 지운다. */
    public void abort(long memberId, String key) {
        try {
            redis.delete(redisKey(memberId, key));
        } catch (DataAccessException e) {
            log.warn("publish idempotency key not cleared (expires by ttl): {}", e.getMessage());
        }
    }

    /** 요청 내용 요약: postId와 정리 전 입력값(순서 고정)의 SHA-256. */
    public static String hash(long postId, PublishCommand command) {
        StringBuilder canonical = new StringBuilder().append(postId).append('\u0000')
                .append(command.title()).append('\u0000').append(command.contentMd()).append('\u0000')
                .append(command.visibility()).append('\u0000').append(command.baseVersion()).append('\u0000');
        if (command.tags() != null) {
            command.tags().forEach(t -> canonical.append(t).append('\u0001'));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

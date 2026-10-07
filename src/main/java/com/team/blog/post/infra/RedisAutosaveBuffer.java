package com.team.blog.post.infra;

import com.team.blog.post.application.AutosaveBuffer;
import com.team.blog.post.application.BufferedContent;
import com.team.blog.post.application.PostProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 버퍼: {@code autosave:post:{postId}} Hash + {@code autosave:dirty} Set(04 §2-3).
 * 버전 확인과 저장은 Lua 하나로 원자 처리한다. 현재 버전은 max(Hash 버전, DB 버전)이다(research R-2).
 */
@Component
public class RedisAutosaveBuffer implements AutosaveBuffer {

    static final String DIRTY_KEY = "autosave:dirty";

    static final String SAVE_SCRIPT = """
            local cur = tonumber(ARGV[3])
            local stored = redis.call('HGET', KEYS[1], 'version')
            if stored then
              if redis.call('HGET', KEYS[1], 'memberId') ~= ARGV[1] then return {-1, 0} end
              stored = tonumber(stored)
              if stored > cur then cur = stored end
            end
            if tonumber(ARGV[2]) ~= cur then return {0, cur} end
            local nextVersion = cur + 1
            redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'title', ARGV[4], 'contentMd', ARGV[5],
                       'version', nextVersion, 'savedAt', ARGV[6])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[7]))
            redis.call('SADD', KEYS[2], ARGV[8])
            return {1, nextVersion}
            """;

    static final String MARK_FLUSHED_SCRIPT = """
            local stored = redis.call('HGET', KEYS[1], 'version')
            if (not stored) or tonumber(stored) <= tonumber(ARGV[1]) then
              redis.call('SREM', KEYS[2], ARGV[2])
              return 1
            end
            return 0
            """;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SAVE = RedisScript.of(SAVE_SCRIPT, List.class);
    private static final RedisScript<Long> MARK_FLUSHED = RedisScript.of(MARK_FLUSHED_SCRIPT, Long.class);

    private final StringRedisTemplate redis;
    private final PostProperties properties;

    public RedisAutosaveBuffer(StringRedisTemplate redis, PostProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    static String key(long postId) {
        return "autosave:post:" + postId;
    }

    @Override
    public SaveOutcome save(long postId, long memberId, long baseVersion, long dbVersion, String title, String contentMd,
                            Instant savedAt) {
        @SuppressWarnings("unchecked")
        List<Object> raw = redis.execute(SAVE, List.of(key(postId), DIRTY_KEY),
                String.valueOf(memberId), String.valueOf(baseVersion), String.valueOf(dbVersion), title, contentMd,
                savedAt.toString(), String.valueOf(properties.autosave().bufferTtl().toSeconds()), String.valueOf(postId));
        if (raw == null || raw.size() < 2) {
            throw new IllegalStateException("autosave script returned no result");
        }
        long result = ((Number) raw.get(0)).longValue();
        long version = ((Number) raw.get(1)).longValue();
        SaveOutcome.Kind kind = result == 1 ? SaveOutcome.Kind.ACCEPTED
                : result == 0 ? SaveOutcome.Kind.CONFLICT : SaveOutcome.Kind.NOT_OWNER;
        return new SaveOutcome(kind, version);
    }

    @Override
    public Optional<BufferedContent> read(long postId) {
        Map<Object, Object> hash = redis.opsForHash().entries(key(postId));
        if (hash == null || hash.get("version") == null) {
            return Optional.empty();
        }
        return Optional.of(new BufferedContent(
                Long.parseLong((String) hash.get("memberId")),
                (String) hash.getOrDefault("title", ""),
                (String) hash.getOrDefault("contentMd", ""),
                Long.parseLong((String) hash.get("version")),
                Instant.parse((String) hash.get("savedAt"))));
    }

    @Override
    public boolean exists(long postId) {
        return Boolean.TRUE.equals(redis.hasKey(key(postId)));
    }

    @Override
    public List<Long> dirtyBatch(int max) {
        List<Long> ids = new ArrayList<>();
        try (Cursor<String> cursor = redis.opsForSet().scan(DIRTY_KEY, ScanOptions.scanOptions().count(max).build())) {
            while (cursor.hasNext() && ids.size() < max) {
                String value = cursor.next();
                try {
                    ids.add(Long.parseLong(value));
                } catch (NumberFormatException e) {
                    redis.opsForSet().remove(DIRTY_KEY, value);
                }
            }
        }
        return ids;
    }

    @Override
    public void markFlushed(long postId, long flushedVersion) {
        redis.execute(MARK_FLUSHED, List.of(key(postId), DIRTY_KEY), String.valueOf(flushedVersion), String.valueOf(postId));
    }

    @Override
    public void evict(long postId) {
        redis.delete(key(postId));
        redis.opsForSet().remove(DIRTY_KEY, String.valueOf(postId));
    }
}

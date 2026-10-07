package com.team.blog.account.infra;

import com.team.blog.account.application.AuthProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 인증·재설정 1회용 토큰 저장소(research R-5, contracts/redis-keys.md).
 * <ul>
 *   <li>토큰 = {@link SecureRandom} 32바이트 → Base64 URL-safe(패딩 없음). 원문은 메일 링크에만 쓴다.</li>
 *   <li>Redis 키 = {@code auth:verify:{sha256(token)}} / {@code auth:reset:{sha256(token)}}, 값 = memberId. 원문은 저장하지 않는다.</li>
 *   <li>회원별 현재 토큰 포인터 {@code auth:*-current:{memberId}}로 새 토큰을 발급하면 이전 토큰을 지운다(FR-009).</li>
 *   <li>사용은 {@code GETDEL}로 원자적으로 한 번만 성공한다(FR-008, FR-017).</li>
 * </ul>
 */
@Component
public class RedisTokenStore {

    private static final int TOKEN_BYTES = 32;

    /** KEYS[1]=새 토큰 키, KEYS[2]=현재 포인터 키, ARGV[1]=memberId, ARGV[2]=TTL(ms), ARGV[3]=토큰 키 접두어, ARGV[4]=새 해시 */
    private static final RedisScript<Long> ISSUE = RedisScript.of("""
            local old = redis.call('GET', KEYS[2])
            if old then redis.call('DEL', ARGV[3] .. old) end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[2], ARGV[4], 'PX', ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RedisTokenStore(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 새 토큰을 발급하고 그 회원의 이전 토큰(같은 종류)을 무효로 한다. @return 원문 토큰(메일 링크용) */
    public String issue(TokenKind kind, long memberId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = KeyHashing.sha256(raw);
        redis.execute(ISSUE, List.of(kind.tokenKey(hash), kind.currentKey(memberId)),
                String.valueOf(memberId), String.valueOf(ttl(kind).toMillis()), kind.tokenPrefix(), hash);
        return raw;
    }

    /** 토큰을 한 번만 꺼낸다({@code GETDEL}). 만료·사용됨·없음이면 empty. */
    public Optional<Long> consume(TokenKind kind, String rawToken) {
        if (!looksLikeToken(rawToken)) {
            return Optional.empty();
        }
        String hash = KeyHashing.sha256(rawToken);
        String value = redis.opsForValue().getAndDelete(kind.tokenKey(hash));
        if (value == null) {
            return Optional.empty();
        }
        long memberId = Long.parseLong(value);
        // 포인터가 이 토큰을 가리키면 함께 지운다(다음 발급 때 지울 것이 없도록)
        String currentKey = kind.currentKey(memberId);
        if (hash.equals(redis.opsForValue().get(currentKey))) {
            redis.delete(currentKey);
        }
        return Optional.of(memberId);
    }

    /** 소비하지 않고 확인만(재설정 화면 표시용). */
    public Optional<Long> peek(TokenKind kind, String rawToken) {
        if (!looksLikeToken(rawToken)) {
            return Optional.empty();
        }
        String value = redis.opsForValue().get(kind.tokenKey(KeyHashing.sha256(rawToken)));
        return value == null ? Optional.empty() : Optional.of(Long.parseLong(value));
    }

    public Duration ttl(TokenKind kind) {
        return kind == TokenKind.VERIFY ? properties.verifyTokenTtl() : properties.resetTokenTtl();
    }

    private static boolean looksLikeToken(String raw) {
        return raw != null && !raw.isEmpty() && raw.length() <= 100 && raw.matches("[A-Za-z0-9_-]+");
    }
}

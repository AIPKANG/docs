package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.infra.KeyHashing;
import com.team.blog.account.infra.RedisTokenStore;
import com.team.blog.account.infra.TokenKind;
import com.team.blog.support.IntegrationTestBase;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 001 T115: 1회용 토큰 저장소(contracts/redis-keys.md, research R-5). */
class RedisTokenStoreIT extends IntegrationTestBase {

    @Autowired
    RedisTokenStore tokens;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void issuedTokenIs32BytesUrlSafeAndKeyHoldsOnlyTheHash() {
        String raw = tokens.issue(TokenKind.VERIFY, 7L);
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(raw)).hasSize(32);

        Set<String> keys = redis.keys("auth:*");
        assertThat(keys).containsExactlyInAnyOrder("auth:verify:" + KeyHashing.sha256(raw), "auth:verify-current:7");
        assertThat(keys).noneMatch(k -> k.contains(raw));
        assertThat(redis.opsForValue().get("auth:verify:" + KeyHashing.sha256(raw))).isEqualTo("7");
        assertThat(redis.opsForValue().get("auth:verify-current:7")).doesNotContain(raw);
    }

    @Test
    void tokenIsConsumedOnlyOnce() {
        String raw = tokens.issue(TokenKind.VERIFY, 7L);
        assertThat(tokens.peek(TokenKind.VERIFY, raw)).contains(7L);
        assertThat(tokens.consume(TokenKind.VERIFY, raw)).contains(7L);
        assertThat(tokens.consume(TokenKind.VERIFY, raw)).isEmpty();
        assertThat(tokens.peek(TokenKind.VERIFY, raw)).isEmpty();
        assertThat(redis.keys("auth:*")).isEmpty();
    }

    @Test
    void newTokenInvalidatesThePreviousOneOfTheSameKindAndMember() {
        String first = tokens.issue(TokenKind.RESET, 7L);
        String other = tokens.issue(TokenKind.RESET, 8L);
        String verify = tokens.issue(TokenKind.VERIFY, 7L);
        String second = tokens.issue(TokenKind.RESET, 7L);

        assertThat(tokens.consume(TokenKind.RESET, first)).isEmpty();
        assertThat(tokens.consume(TokenKind.RESET, second)).contains(7L);
        assertThat(tokens.consume(TokenKind.RESET, other)).contains(8L);
        assertThat(tokens.consume(TokenKind.VERIFY, verify)).contains(7L);
    }

    @Test
    void ttlIs24HoursForVerifyAnd30MinutesForReset() {
        String verify = tokens.issue(TokenKind.VERIFY, 1L);
        String reset = tokens.issue(TokenKind.RESET, 1L);
        Long verifyTtl = redis.getExpire("auth:verify:" + KeyHashing.sha256(verify), TimeUnit.SECONDS);
        Long resetTtl = redis.getExpire("auth:reset:" + KeyHashing.sha256(reset), TimeUnit.SECONDS);
        assertThat(verifyTtl).isBetween(24 * 3600L - 5, 24 * 3600L);
        assertThat(resetTtl).isBetween(30 * 60L - 5, 30 * 60L);
        assertThat(redis.getExpire("auth:verify-current:1", TimeUnit.SECONDS)).isBetween(24 * 3600L - 5, 24 * 3600L);
    }

    @Test
    void expiredOrGarbageTokensAreRejected() {
        String raw = tokens.issue(TokenKind.VERIFY, 7L);
        redis.delete("auth:verify:" + KeyHashing.sha256(raw)); // TTL 만료와 같은 상태
        assertThat(tokens.consume(TokenKind.VERIFY, raw)).isEmpty();
        assertThat(tokens.consume(TokenKind.VERIFY, "")).isEmpty();
        assertThat(tokens.consume(TokenKind.VERIFY, null)).isEmpty();
        assertThat(tokens.consume(TokenKind.VERIFY, "not a token!")).isEmpty();
    }
}

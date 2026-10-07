package com.team.blog.account.infra;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 로그인 실패 카운터·잠금 키(contracts/redis-keys.md). 키에는 이메일 해시만 쓴다.
 * <ul>
 *   <li>{@code auth:login-fail:{sha256(email)}}: 연속 실패 수, 실패마다 TTL 연장</li>
 *   <li>{@code auth:login-lock:{sha256(email)}}: 잠금 표시(TTL = 잠금 시간)</li>
 * </ul>
 */
@Component
public class LoginAttemptStore {

    /** KEYS[1]=fail, KEYS[2]=lock, ARGV[1]=TTL(ms), ARGV[2]=최대 실패 수. 반환: 1이면 이번 실패로 잠김. */
    private static final RedisScript<Long> RECORD_FAILURE = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            if count >= tonumber(ARGV[2]) then
              redis.call('SET', KEYS[2], '1', 'PX', ARGV[1])
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public LoginAttemptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public static String failKey(String email) {
        return "auth:login-fail:" + KeyHashing.emailHash(email);
    }

    public static String lockKey(String email) {
        return "auth:login-lock:" + KeyHashing.emailHash(email);
    }

    public boolean isLocked(String email) {
        return isLockedKey(lockKey(email));
    }

    /** @return 이번 실패로 잠겼으면 true */
    public boolean recordFailure(String email, int maxFailures, Duration lockDuration) {
        return recordFailureKeys(failKey(email), lockKey(email), maxFailures, lockDuration);
    }

    public void clearFailures(String email) {
        clearKey(failKey(email));
    }

    // ----- 003: 키를 직접 받는 형태(비밀번호 변경 잠금 auth:pw-change-fail/lock:{memberId}) -----

    public static String passwordChangeFailKey(long memberId) {
        return "auth:pw-change-fail:" + memberId;
    }

    public static String passwordChangeLockKey(long memberId) {
        return "auth:pw-change-lock:" + memberId;
    }

    public boolean isLockedKey(String lockKey) {
        return Boolean.TRUE.equals(redis.hasKey(lockKey));
    }

    /** 연속 실패 +1(TTL 연장), {@code maxFailures}번째에 잠금 키를 {@code lockDuration} 동안 만든다. */
    public boolean recordFailureKeys(String failKey, String lockKey, int maxFailures, Duration lockDuration) {
        Long locked = redis.execute(RECORD_FAILURE, java.util.List.of(failKey, lockKey),
                String.valueOf(lockDuration.toMillis()), String.valueOf(maxFailures));
        return locked != null && locked == 1L;
    }

    public void clearKey(String key) {
        redis.delete(key);
    }

    /** 잠금 남은 시간(초, 최소 1). 잠금이 없으면 0. */
    public long remainingSeconds(String lockKey) {
        Long ttl = redis.getExpire(lockKey);
        return ttl == null || ttl < 0 ? 0 : Math.max(1, ttl);
    }
}

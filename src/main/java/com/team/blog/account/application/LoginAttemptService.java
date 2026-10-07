package com.team.blog.account.application;

import com.team.blog.account.infra.LoginAttemptStore;
import com.team.blog.account.infra.RedisRateLimiter;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * 로그인 잠금·IP 한도(FR-025, research R-6). Redis만 쓴다. 없는 이메일에도 똑같이 적용해 가입 여부를 드러내지 않는다(SC-006).
 */
@Service
public class LoginAttemptService {

    private final LoginAttemptStore store;
    private final RedisRateLimiter rateLimiter;
    private final AuthProperties properties;

    public LoginAttemptService(LoginAttemptStore store, RedisRateLimiter rateLimiter, AuthProperties properties) {
        this.store = store;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /**
     * 인증 전에 부른다: IP 1분 한도(시도마다 1 증가) → 계정 잠금.
     *
     * @throws IpRateLimitedException IP 한도 초과
     * @throws LoginLockedException   잠금 중
     */
    public void checkAllowed(String emailNorm, String ip) {
        RedisRateLimiter.Result ipResult = rateLimiter.tryAcquire(RedisRateLimiter.key("auth:login-ip", ip),
                properties.login().ipPerMinute(), Duration.ofMinutes(1));
        if (!ipResult.allowed()) {
            throw new IpRateLimitedException();
        }
        if (store.isLocked(emailNorm)) {
            throw new LoginLockedException();
        }
    }

    /** 자격 증명 불일치: 연속 실패 +1(TTL 연장), 최대 횟수째에 잠금. */
    public void recordFailure(String emailNorm) {
        store.recordFailure(emailNorm, properties.login().maxFailures(), properties.login().lockDuration());
    }

    /** 로그인 성공: 실패 카운터 삭제. */
    public void recordSuccess(String emailNorm) {
        store.clearFailures(emailNorm);
    }
}

package com.team.blog.account.web;

import com.team.blog.account.application.AccountIdentityProperties;
import com.team.blog.account.application.NicknameCheckResult;
import com.team.blog.account.application.NicknamePolicy;
import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 닉네임 실시간 확인 API(로그인 불필요, contracts/web-routes.md §1). 화면은 {@code static/js/account/availability.js}가
 * {@code data-availability="nickname"} 칸에 연결한다. IP 버킷 {@code account:nickname-check:ip:{ip}}(1분 30회).
 * 로그인한 회원이 부르면 자기 자신을 중복에서 뺀다.
 */
@RestController
public class NicknameApiController {

    static final String BUCKET = "account:nickname-check:ip";

    private final NicknamePolicy nicknamePolicy;
    private final CurrentUserProvider currentUserProvider;
    private final RedisRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final AccountIdentityProperties properties;

    public NicknameApiController(NicknamePolicy nicknamePolicy, CurrentUserProvider currentUserProvider,
                                 RedisRateLimiter rateLimiter, ClientIpResolver clientIpResolver,
                                 AccountIdentityProperties properties) {
        this.nicknamePolicy = nicknamePolicy;
        this.currentUserProvider = currentUserProvider;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
    }

    /** @param code 09 §3 다섯 코드 중 첫 실패, 사용 가능하면 null */
    public record NicknameAvailability(boolean available, String code) {
    }

    @GetMapping("/api/nicknames/availability")
    public NicknameAvailability availability(@RequestParam(name = "nickname", defaultValue = "") String nickname,
                                             HttpServletRequest request) {
        RedisRateLimiter.Result limit = rateLimiter.tryAcquire(
                RedisRateLimiter.key(BUCKET, clientIpResolver.resolve(request)),
                properties.availability().perIpPerMinute(), Duration.ofMinutes(1));
        if (!limit.allowed()) {
            throw new RateLimitedException(limit.retryAfterSeconds());
        }
        Long excludeMemberId = currentUserProvider.current().map(CurrentUser::memberId).orElse(null);
        NicknameCheckResult result = nicknamePolicy.check(nickname, excludeMemberId);
        return new NicknameAvailability(result.available(), result.available() ? null : result.violation().name());
    }
}

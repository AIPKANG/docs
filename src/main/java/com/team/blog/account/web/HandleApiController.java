package com.team.blog.account.web;

import com.team.blog.account.application.AccountIdentityProperties;
import com.team.blog.account.application.HandleCheckResult;
import com.team.blog.account.application.HandleService;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 블로그 주소 실시간 확인 API(로그인 불필요, contracts/web-routes.md §1).
 * 두 경로가 IP 버킷 {@code account:handle-check:ip:{ip}}(1분 30회)를 함께 쓴다. 이메일은 URL·로그에 남기지 않는다.
 */
@RestController
@RequestMapping("/api/handles")
public class HandleApiController {

    static final String BUCKET = "account:handle-check:ip";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+$");
    private static final int MAX_EMAIL_LENGTH = 254;

    private final HandleService handleService;
    private final RedisRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final AccountIdentityProperties properties;

    public HandleApiController(HandleService handleService, RedisRateLimiter rateLimiter,
                               ClientIpResolver clientIpResolver, AccountIdentityProperties properties) {
        this.handleService = handleService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
    }

    @GetMapping("/availability")
    public HandleCheckResult availability(@RequestParam(name = "handle", defaultValue = "") String handle,
                                          HttpServletRequest request) {
        limit(request);
        return handleService.checkAvailability(handle);
    }

    /** 요청 본문 {@code {"email": "..."}}. CSRF 토큰 필수(상태를 바꾸지 않지만 POST라 Security가 검사). */
    public record SuggestionRequest(String email) {
    }

    public record SuggestionResponse(String handle) {
    }

    @PostMapping("/suggestion")
    public SuggestionResponse suggestion(@RequestBody(required = false) SuggestionRequest body, HttpServletRequest request) {
        limit(request);
        String email = body == null ? null : body.email();
        if (email == null || email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email.strip()).matches()) {
            return new SuggestionResponse(null);
        }
        return new SuggestionResponse(handleService.prefill(email.strip(), Provider.LOCAL));
    }

    private void limit(HttpServletRequest request) {
        String key = RedisRateLimiter.key(BUCKET, clientIpResolver.resolve(request));
        RedisRateLimiter.Result result = rateLimiter.tryAcquire(
                key, properties.availability().perIpPerMinute(), Duration.ofMinutes(1));
        if (!result.allowed()) {
            throw new RateLimitedException(result.retryAfterSeconds());
        }
    }
}

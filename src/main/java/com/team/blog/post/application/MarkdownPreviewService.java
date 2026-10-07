package com.team.blog.post.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.post.markdown.ContentRenderer;
import com.team.blog.post.markdown.MarkdownProperties;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 미리보기(007 FR-020, 12 §7-6): 로그인 회원, 사용자당 1분 60번, 발행과 같은 렌더러. */
@Service
public class MarkdownPreviewService {

    private final AccountGuard accountGuard;
    private final ContentRenderer renderer;
    private final RedisRateLimiter rateLimiter;
    private final MarkdownProperties markdownProperties;
    private final PostProperties postProperties;

    public MarkdownPreviewService(AccountGuard accountGuard, ContentRenderer renderer, RedisRateLimiter rateLimiter,
                                  MarkdownProperties markdownProperties, PostProperties postProperties) {
        this.accountGuard = accountGuard;
        this.renderer = renderer;
        this.rateLimiter = rateLimiter;
        this.markdownProperties = markdownProperties;
        this.postProperties = postProperties;
    }

    public String preview(Optional<CurrentUser> currentUser, String contentMd) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        if (contentMd == null) {
            throw new PostContentException("INVALID_REQUEST");
        }
        if (contentMd.codePointCount(0, contentMd.length()) > postProperties.contentMaxLength()) {
            throw new PostContentException("CONTENT_TOO_LONG");
        }
        RedisRateLimiter.Result result = rateLimiter.tryAcquire(
                RedisRateLimiter.key("markdown:preview:member", String.valueOf(user.memberId())),
                markdownProperties.previewPerMinute(), Duration.ofMinutes(1));
        if (!result.allowed()) {
            throw new RateLimitedException(result.retryAfterSeconds());
        }
        return renderer.render(contentMd).html();
    }
}

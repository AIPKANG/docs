package com.team.blog.teaser.application;

import com.team.blog.account.application.AiConsentService;
import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.tag.application.suggest.AiInputCleaner;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** AI 티저(035): 만들기(제안만, 동의·하루 횟수), 저장·지우기(글쓴이만, 글자만). */
@Service
public class TeaserService {

    private final JdbcTemplate jdbc;
    private final AccountGuard accountGuard;
    private final AiConsentService consent;
    private final RedisRateLimiter rateLimiter;
    private final AiTeaserClient client;
    private final TeaserProperties properties;

    public TeaserService(JdbcTemplate jdbc, AccountGuard accountGuard, AiConsentService consent, RedisRateLimiter rateLimiter,
                         AiTeaserClient client, TeaserProperties properties) {
        this.jdbc = jdbc;
        this.accountGuard = accountGuard;
        this.consent = consent;
        this.rateLimiter = rateLimiter;
        this.client = client;
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public Optional<String> current(long postId) {
        return jdbc.queryForList("SELECT teaser FROM post_teaser WHERE post_id = ?", String.class, postId).stream().findFirst();
    }

    /** AI 제안 한 줄(저장하지 않음). 동의가 없으면 {@code TEASER_CONSENT_REQUIRED}, 안 되면 {@code TEASER_UNAVAILABLE}. */
    public String generate(Optional<CurrentUser> current, long postId) {
        CurrentUser me = accountGuard.requireWritable(current);
        Map<String, Object> post = ownPost(me.memberId(), postId);
        if (!consent.hasConsent(me.memberId())) {
            throw new PostContentException("TEASER_CONSENT_REQUIRED");
        }
        RedisRateLimiter.Result r = rateLimiter.tryAcquire(RedisRateLimiter.key("teaser:ai", String.valueOf(me.memberId())),
                properties.perDay(), Duration.ofDays(1));
        if (!r.allowed()) {
            throw new RateLimitedException(r.retryAfterSeconds());
        }
        String text = AiInputCleaner.truncate(AiInputCleaner.clean((String) post.get("title"), (String) post.get("content_md")), 2000);
        return client.write(text, properties.maxLength()).orElseThrow(() -> new PostContentException("TEASER_UNAVAILABLE"));
    }

    public void save(Optional<CurrentUser> current, long postId, String raw) {
        CurrentUser me = accountGuard.requireWritable(current);
        ownPost(me.memberId(), postId);
        String teaser = AiTeaserClient.oneLine(raw, properties.maxLength());
        if (teaser.isEmpty()) {
            jdbc.update("DELETE FROM post_teaser WHERE post_id = ?", postId);
            return;
        }
        jdbc.update("""
                INSERT INTO post_teaser (post_id, teaser) VALUES (?, ?)
                ON CONFLICT (post_id) DO UPDATE SET teaser = EXCLUDED.teaser, created_at = now()
                """, postId, teaser);
    }

    private Map<String, Object> ownPost(long me, long postId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT title, content_md FROM post WHERE id = ? AND author_id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL",
                postId, me);
        if (rows.isEmpty()) {
            throw new NotFoundException();
        }
        return rows.get(0);
    }
}

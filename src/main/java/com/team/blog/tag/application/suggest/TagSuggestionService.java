package com.team.blog.tag.application.suggest;

import com.team.blog.account.application.AiConsentService;
import com.team.blog.post.application.PostProperties;
import com.team.blog.post.application.TagListingQuery;
import com.team.blog.shared.error.AiSuggestException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.tag.domain.TagNormalizer;
import com.team.blog.tag.domain.TagRejectedException;
import com.team.blog.tag.infra.GeminiTagSuggester;
import com.team.blog.tag.infra.OllamaTagSuggester;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 태그 추천(021, 34). 판정 순서: 기능 꺼짐(503) → 로그인·인증(401/403) → 자기 글(404) → 공개 글만(400) → 동의(409)
 * → 정리 후 100자 미만(422, 횟수 안 셈) → 재사용(① 같은 내용 ② 비슷한 내용, 횟수 안 셈) → 개인 하루 20회(429)
 * → Gemini(가능하면) / Ollama → 정규화·거르기. 글 내용·키는 로그에 남기지 않는다.
 */
@Service
public class TagSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(TagSuggestionService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public record Request(String title, String contentMd, List<String> currentTags, String visibility, boolean refresh) {
    }

    public record Result(List<String> tags, String provider, boolean cached, boolean truncated, int remainingToday,
                         String message) {
    }

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final AccountGuard accountGuard;
    private final AiConsentService consentService;
    private final AiTagProperties properties;
    private final PostProperties postProperties;
    private final AiSuggestionCache cache;
    private final AiProviderState state;
    private final GeminiTagSuggester gemini;
    private final OllamaTagSuggester ollama;
    private final TagNormalizer normalizer;
    private final TagListingQuery tagListingQuery;
    private final Clock clock;

    public TagSuggestionService(JdbcTemplate jdbc, StringRedisTemplate redis, AccountGuard accountGuard,
                                AiConsentService consentService, AiTagProperties properties, PostProperties postProperties,
                                AiSuggestionCache cache, AiProviderState state, GeminiTagSuggester gemini,
                                OllamaTagSuggester ollama, TagNormalizer normalizer, TagListingQuery tagListingQuery, Clock clock) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.accountGuard = accountGuard;
        this.consentService = consentService;
        this.properties = properties;
        this.postProperties = postProperties;
        this.cache = cache;
        this.state = state;
        this.gemini = gemini;
        this.ollama = ollama;
        this.normalizer = normalizer;
        this.tagListingQuery = tagListingQuery;
        this.clock = clock;
    }

    public Result suggest(Optional<CurrentUser> current, long postId, Request request) {
        if (!properties.enabled()) {
            throw new AiSuggestException(AiSuggestException.UNAVAILABLE);
        }
        CurrentUser user = accountGuard.requireWritable(current);
        record Owned(String status, String visibility) {
        }
        Owned post = jdbc.query("SELECT status, visibility FROM post WHERE id = ? AND author_id = ? AND deleted_at IS NULL",
                        (rs, n) -> new Owned(rs.getString(1), rs.getString(2)), postId, user.memberId())
                .stream().findFirst().orElseThrow(NotFoundException::new);
        boolean publicPost = "PUBLIC".equals(request.visibility())
                && ("DRAFT".equals(post.status()) || "PUBLIC".equals(post.visibility()));
        if (!publicPost) {
            throw new AiSuggestException(AiSuggestException.PUBLIC_ONLY);
        }
        if (!consentService.hasConsent(user.memberId())) {
            throw new AiSuggestException(AiSuggestException.CONSENT_REQUIRED);
        }
        String input = AiInputCleaner.clean(request.title(), request.contentMd());
        if (AiInputCleaner.length(input) < properties.minChars()) {
            throw new AiSuggestException(AiSuggestException.CONTENT_TOO_SHORT);
        }
        Set<String> currentTags = new LinkedHashSet<>();
        for (String t : request.currentTags() == null ? List.<String>of() : request.currentTags()) {
            TagNormalizer.lookupName(t).ifPresent(currentTags::add);
        }
        int slots = Math.max(0, Math.min(properties.maxSuggestions(), postProperties.maxTags() - currentTags.size()));
        int used = usedToday(user.memberId());

        Optional<AiSuggestionCache.Entry> hit = cache.exact(input);
        if (hit.isPresent() && request.refresh() && "ollama".equals(hit.get().provider()) && geminiUsable()) {
            hit = Optional.empty(); // [다시 추천]: 자체 AI 결과면 외부 AI로 다시
        }
        if (hit.isEmpty() && !request.refresh()) {
            hit = cache.similar(postId, input);
        }
        if (hit.isPresent()) {
            return respond(hit.get().tags(), hit.get().provider(), true, input, currentTags, slots, used);
        }
        if (used >= properties.perUserDaily()) {
            throw new AiSuggestException(AiSuggestException.DAILY_LIMIT);
        }
        List<String> popular = popular();
        String provider;
        List<String> raw;
        countUse(user.memberId());
        used++;
        if (geminiUsable()) {
            try {
                raw = gemini.suggest(new TagSuggester.Prompt(AiInputCleaner.truncate(input, properties.gemini().maxChars()),
                        popular, List.copyOf(currentTags), properties.maxSuggestions()));
                state.geminiSucceeded();
                return store(postId, input, raw, "gemini", currentTags, slots, used);
            } catch (TagSuggester.ProviderException e) {
                log.warn("gemini tag suggestion failed: {}", e.kind());
                switch (e.kind()) {
                    case DAILY_LIMIT -> state.markExhausted();
                    case MINUTE_LIMIT -> state.cooldown();
                    case UNKNOWN_LIMIT -> state.unknownLimit();
                    case TIMEOUT_OR_SERVER -> {
                        state.cooldown();
                        throw new AiSuggestException(AiSuggestException.UNAVAILABLE);
                    }
                    default -> throw new AiSuggestException(AiSuggestException.UNAVAILABLE);
                }
            }
        }
        // 자체 AI: 동시 처리 수 제한, 인기 태그는 글에 실제로 나오는 것만
        if (!state.acquireOllama()) {
            throw new AiSuggestException(AiSuggestException.BUSY);
        }
        try {
            String shortInput = AiInputCleaner.truncate(input, properties.ollama().maxChars());
            String lower = shortInput.toLowerCase(Locale.ROOT);
            raw = ollama.suggest(new TagSuggester.Prompt(shortInput,
                    popular.stream().filter(t -> lower.contains(t.toLowerCase(Locale.ROOT))).toList(),
                    List.copyOf(currentTags), properties.maxSuggestions()));
            provider = "ollama";
        } catch (TagSuggester.ProviderException e) {
            log.warn("ollama tag suggestion failed: {}", e.kind());
            throw new AiSuggestException(AiSuggestException.UNAVAILABLE);
        } finally {
            state.releaseOllama();
        }
        return store(postId, input, raw, provider, currentTags, slots, used);
    }

    private boolean geminiUsable() {
        try {
            return gemini.configured() && state.geminiAvailable();
        } catch (DataAccessException e) {
            return false;
        }
    }

    /**
     * 정규화(013 규칙)·금칙어·형식 거르기, 중복 제거. 저장은 이미 붙인 태그를 빼기 전 결과로 하되, 이미 붙인 태그까지 빼고 나서
     * 남는 것이 없으면(검증에서 모두 걸러짐) 저장하지 않는다(FR-026).
     */
    private Result store(long postId, String input, List<String> raw, String provider, Set<String> currentTags, int slots,
                         int used) {
        List<String> clean = normalize(raw);
        if (clean.stream().anyMatch(t -> !currentTags.contains(t))) {
            cache.put(postId, input, clean, provider);
        }
        return respond(clean, provider, false, input, currentTags, slots, used);
    }

    private List<String> normalize(List<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        for (String t : raw) {
            try {
                result.add(normalizer.normalize(t));
            } catch (TagRejectedException e) {
                // 규칙·금칙어에 걸린 태그는 뺀다
            }
        }
        return new ArrayList<>(result).subList(0, Math.min(result.size(), properties.maxSuggestions()));
    }

    private Result respond(List<String> tags, String provider, boolean cached, String input, Set<String> currentTags,
                           int slots, int used) {
        List<String> shown = normalize(tags).stream().filter(t -> !currentTags.contains(t)).limit(slots).toList();
        int max = "ollama".equals(provider) ? properties.ollama().maxChars() : properties.gemini().maxChars();
        return new Result(shown, provider, cached, AiInputCleaner.length(input) > max,
                Math.max(0, properties.perUserDaily() - used), shown.isEmpty() ? "추천할 태그를 찾지 못했어요" : null);
    }

    private String userKey(long memberId) {
        return "ai:tag:user:" + memberId + ":" + LocalDate.ofInstant(clock.instant(), SEOUL);
    }

    private int usedToday(long memberId) {
        try {
            String v = redis.opsForValue().get(userKey(memberId));
            return v == null ? 0 : Integer.parseInt(v);
        } catch (DataAccessException e) {
            return 0;
        }
    }

    private void countUse(long memberId) {
        try {
            redis.opsForValue().increment(userKey(memberId));
            redis.expire(userKey(memberId), Duration.ofHours(25));
        } catch (DataAccessException e) {
            // 횟수 저장소가 멈춰도 추천은 진행
        }
    }

    /** 인기 태그 상위 N(하루 1번 갱신, {@code ai:tag:popular:{날짜}}). */
    private List<String> popular() {
        String key = "ai:tag:popular:" + LocalDate.ofInstant(clock.instant(), SEOUL);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return cached.isEmpty() ? List.of() : List.of(cached.split(","));
            }
            List<String> names = tagListingQuery.top(properties.popularCount()).stream().map(TagListingQuery.TagCount::name).toList();
            redis.opsForValue().set(key, String.join(",", names), Duration.ofDays(1));
            return names;
        } catch (DataAccessException e) {
            return List.of();
        }
    }
}

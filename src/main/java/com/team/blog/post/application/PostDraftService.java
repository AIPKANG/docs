package com.team.blog.post.application;

import com.team.blog.account.application.AccountSettingsService;
import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.post.domain.PostContentRules;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostEditStore;
import com.team.blog.shared.error.EditConflictException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.error.SaveDelayedException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 새 글·자동 저장·수동 저장·변경 취소(004, contracts/post-edit-service.md). 판정 순서는 42 §3:
 * 로그인·계정 상태(401/403) → 소유(남의 글·없는 글·휴지통 404) → 입력 규칙(400) → 요청 제한(429) → 버전(409).
 * 버전 확인은 버퍼(Redis Lua) 한 곳에서 하고, 버퍼에 닿지 못하면 DB 행 잠금으로 같은 확인을 한다(research R-6).
 */
@Service
public class PostDraftService {

    private static final Logger log = LoggerFactory.getLogger(PostDraftService.class);

    private final AccountGuard accountGuard;
    private final AccountSettingsService accountSettings;
    private final PostEditStore store;
    private final AutosaveBuffer buffer;
    private final AutosaveFlusher flusher;
    private final BufferCircuit circuit;
    private final RedisRateLimiter rateLimiter;
    private final PostProperties properties;
    private final Clock clock;
    private final com.team.blog.tag.application.PostTagService tagService;

    public PostDraftService(AccountGuard accountGuard, AccountSettingsService accountSettings, PostEditStore store,
                            AutosaveBuffer buffer, AutosaveFlusher flusher, BufferCircuit circuit,
                            RedisRateLimiter rateLimiter, PostProperties properties, Clock clock,
                            com.team.blog.tag.application.PostTagService tagService) {
        this.accountGuard = accountGuard;
        this.accountSettings = accountSettings;
        this.store = store;
        this.buffer = buffer;
        this.flusher = flusher;
        this.circuit = circuit;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
        this.tagService = tagService;
    }

    /** [새 글](FR-015): 편집 버전 0인 임시글, 공개 범위는 회원 기본값. */
    public long create(Optional<CurrentUser> currentUser, String title, String contentMd) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        String t = PostContentRules.normalizeTitle(title);
        String c = PostContentRules.normalizeContent(contentMd);
        validate(t, c);
        String visibility = accountSettings.defaultVisibility(user.memberId());
        return store.insertDraft(user.memberId(), t, c, visibility, clock.instant());
    }

    /** 편집 화면의 현재 내용(research R-2). 작성자만, 휴지통 글은 404. */
    public EditingContent editing(Optional<CurrentUser> currentUser, long postId) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        return current(owned(user, postId));
    }

    /** 자동 저장(FR-005, FR-008, FR-016~FR-018). */
    public SaveResult autosave(Optional<CurrentUser> currentUser, long postId, SaveCommand command) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        PostEditRow row = owned(user, postId);
        Input input = input(command);
        checkRateLimit(user.memberId());
        return store(user, row, input).result();
    }

    /** 수동 저장(FR-002): 같은 버전 확인 뒤 바로 DB에 반영한다. */
    public SaveResult save(Optional<CurrentUser> currentUser, long postId, SaveCommand command) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        PostEditRow row = owned(user, postId);
        Input input = input(command);
        Stored stored = store(user, row, input);
        if (stored.viaBuffer()) {
            try {
                flusher.flushOne(postId);
            } catch (RuntimeException e) {
                log.warn("manual save flush failed for post {}: {}", postId, e.getMessage());
                throw new SaveDelayedException(stored.result().version(), e);
            }
        }
        return stored.result();
    }

    /** [변경 취소](FR-024): 작업본과 버퍼를 지운다. 발행본은 그대로. 임시글은 404. */
    public void discardWorkingCopy(Optional<CurrentUser> currentUser, long postId) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        PostEditRow row = owned(user, postId);
        if (row.status() != PostStatus.PUBLISHED) {
            throw new NotFoundException();
        }
        long currentVersion = current(row).version();
        if (!store.discardWorkingCopy(postId, currentVersion)) {
            throw new NotFoundException();
        }
        // 트랜잭션은 store 안에서 커밋됐다 — 그 뒤에 버퍼를 지운다(커밋 전에 지우면 실패 시 내용이 사라짐)
        try {
            buffer.evict(postId);
        } catch (DataAccessException e) {
            circuit.recordFailure();
            log.warn("autosave buffer evict failed for post {} (stale buffer is ignored by version): {}", postId, e.getMessage());
        }
    }

    /** 발행 설정 창의 시작 값(005): 글의 공개 범위와 지금 달린 태그(순서대로). */
    public record PublishDefaults(String visibility, java.util.List<String> tags) {
    }

    public PublishDefaults publishDefaults(Optional<CurrentUser> currentUser, long postId) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        PostEditRow row = owned(user, postId);
        return new PublishDefaults(row.visibility(), tagService.tagsOf(postId));
    }

    /** 발행 글을 고치는 중인지(작업본 또는 발행본보다 새 버퍼). */
    public boolean isEditing(PostEditRow row) {
        return row.status() == PostStatus.PUBLISHED && current(row).editing();
    }

    // ----- 내부 -----

    private record Input(String title, String contentMd, long baseVersion) {
    }

    private record Stored(SaveResult result, boolean viaBuffer) {
    }

    private PostEditRow owned(CurrentUser user, long postId) {
        return store.findActive(postId)
                .filter(row -> row.authorId() == user.memberId())
                .orElseThrow(NotFoundException::new);
    }

    private Input input(SaveCommand command) {
        if (command == null || command.baseVersion() == null || command.baseVersion() < 0) {
            throw new PostContentException(PostContentRules.INVALID_REQUEST);
        }
        String t = PostContentRules.normalizeTitle(command.title());
        String c = PostContentRules.normalizeContent(command.contentMd());
        validate(t, c);
        return new Input(t, c, command.baseVersion());
    }

    private void validate(String title, String content) {
        PostContentRules.firstViolation(title, content, properties.titleMaxLength(), properties.contentMaxLength())
                .ifPresent(code -> {
                    throw new PostContentException(code);
                });
    }

    /** 사용자당 5초 1회(FR-008). 버퍼 저장소가 장애면 제한을 건너뛴다(글 손실 방지가 우선, research R-6). */
    private void checkRateLimit(long memberId) {
        if (!circuit.allowsRedis()) {
            return;
        }
        RedisRateLimiter.Result result;
        try {
            result = rateLimiter.tryAcquire(RedisRateLimiter.key("post:autosave:member", String.valueOf(memberId)), 1,
                    properties.autosave().rateLimitWindow());
        } catch (DataAccessException e) {
            circuit.recordFailure();
            return;
        }
        if (!result.allowed()) {
            throw new RateLimitedException(result.retryAfterSeconds());
        }
    }

    private Stored store(CurrentUser user, PostEditRow row, Input input) {
        Instant now = clock.instant();
        if (circuit.allowsRedis()) {
            try {
                AutosaveBuffer.SaveOutcome outcome = buffer.save(row.id(), user.memberId(), input.baseVersion(),
                        row.dbVersion(), input.title(), input.contentMd(), now);
                return switch (outcome.kind()) {
                    case ACCEPTED -> new Stored(new SaveResult(outcome.version(), now), true);
                    case CONFLICT -> throw conflict(current(row));
                    case NOT_OWNER -> throw new NotFoundException();
                };
            } catch (DataAccessException e) {
                circuit.recordFailure();
                log.warn("autosave buffer unavailable, saving directly to database: {}", e.getMessage());
            }
        }
        PostEditStore.DirectSave direct = store.saveDirect(row.id(), input.baseVersion(), input.title(),
                input.contentMd(), now).orElseThrow(NotFoundException::new);
        if (!direct.accepted()) {
            throw conflict(direct.row().dbContent());
        }
        return new Stored(new SaveResult(direct.version(), now), false);
    }

    /** DB 내용과 버퍼 내용 중 버전이 큰 쪽(같으면 버퍼). 버퍼에 닿지 못하면 DB. 발행(005)도 이 규칙으로 현재 버전을 정한다. */
    public EditingContent current(PostEditRow row) {
        EditingContent db = row.dbContent();
        Optional<BufferedContent> buffered = Optional.empty();
        if (circuit.allowsRedis()) {
            try {
                buffered = buffer.read(row.id());
            } catch (DataAccessException e) {
                circuit.recordFailure();
            }
        }
        if (buffered.isPresent() && buffered.get().memberId() == row.authorId() && buffered.get().version() >= db.version()) {
            BufferedContent b = buffered.get();
            boolean editing = row.status() == PostStatus.PUBLISHED && b.version() > row.postVersion();
            return new EditingContent(row.id(), row.status(), b.title(), b.contentMd(), b.version(), b.savedAt(), editing);
        }
        return db;
    }

    private static EditConflictException conflict(EditingContent server) {
        return new EditConflictException(server.title(), server.contentMd(), server.version(), server.savedAt());
    }
}

package com.team.blog.post.application;

import com.team.blog.account.application.MemberSummaryQuery;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostEditStore;
import com.team.blog.post.markdown.ContentRenderer;
import com.team.blog.post.markdown.RenderedContent;
import com.team.blog.shared.error.EditConflictException;
import com.team.blog.shared.error.IdempotencyKeyReusedException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.PublishInProgressException;
import com.team.blog.shared.event.PostEdited;
import com.team.blog.shared.event.PostPublished;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.tag.application.PostTagService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 발행·다시 발행(005, 05 §7). 판정 순서 42 §3: 로그인·계정 상태 → 소유(404) → 요청 식별자 → 입력 검사(400) → 버전(409).
 * <ol>
 *   <li>트랜잭션 밖: 검사(실패 칸 모두)·렌더링·요약 — CPU 작업을 잠금 전에 끝낸다.</li>
 *   <li>트랜잭션: 행 잠금 → 현재 버전(버퍼·작업본·글 중 최대, 004 R-2) 확인 → 태그 확정 → 글 반영 → 작업본 삭제 → 사건.</li>
 *   <li>커밋 후: 버퍼를 확인한 버전 이하일 때만 삭제(그 사이 들어온 자동 저장 보존) → 요청 식별자에 응답 저장.</li>
 * </ol>
 */
@Service
public class PostPublishService {

    private static final Logger log = LoggerFactory.getLogger(PostPublishService.class);

    private final AccountGuard accountGuard;
    private final PostEditStore store;
    private final PostDraftService draftService;
    private final PublishValidator validator;
    private final PublishIdempotency idempotency;
    private final ContentRenderer renderer;
    private final PostTagService tagService;
    private final AutosaveBuffer buffer;
    private final BufferCircuit circuit;
    private final MemberSummaryQuery memberSummaryQuery;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PostPublishService(AccountGuard accountGuard, PostEditStore store, PostDraftService draftService,
                              PublishValidator validator, PublishIdempotency idempotency, ContentRenderer renderer,
                              PostTagService tagService, AutosaveBuffer buffer, BufferCircuit circuit,
                              MemberSummaryQuery memberSummaryQuery, ApplicationEventPublisher events,
                              TransactionTemplate transactionTemplate, Clock clock) {
        this.accountGuard = accountGuard;
        this.store = store;
        this.draftService = draftService;
        this.validator = validator;
        this.idempotency = idempotency;
        this.renderer = renderer;
        this.tagService = tagService;
        this.buffer = buffer;
        this.circuit = circuit;
        this.memberSummaryQuery = memberSummaryQuery;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public PublishResult publish(Optional<CurrentUser> currentUser, long postId, String idempotencyKey,
                                 PublishCommand command) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        store.findActive(postId).filter(row -> row.authorId() == user.memberId()).orElseThrow(NotFoundException::new);
        if (!PublishIdempotency.validKey(idempotencyKey) || command == null) {
            throw new PostContentException("INVALID_REQUEST");
        }
        String hash = PublishIdempotency.hash(postId, command);
        PublishIdempotency.Begin begin = idempotency.begin(user.memberId(), idempotencyKey, hash);
        switch (begin.state()) {
            case DONE -> {
                return begin.response();
            }
            case IN_PROGRESS -> throw new PublishInProgressException();
            case REUSED -> throw new IdempotencyKeyReusedException();
            default -> {
            }
        }
        boolean tracked = begin.state() == PublishIdempotency.State.NEW;
        try {
            PublishResult result = publishNow(user, postId, command);
            if (tracked) {
                idempotency.complete(user.memberId(), idempotencyKey, hash, result);
            }
            return result;
        } catch (RuntimeException e) {
            if (tracked) {
                idempotency.abort(user.memberId(), idempotencyKey);
            }
            throw e;
        }
    }

    private record Applied(PublishResult result, long checkedVersion) {
    }

    private PublishResult publishNow(CurrentUser user, long postId, PublishCommand command) {
        PublishValidator.Validated input = validator.validate(command);
        RenderedContent rendered = renderer.render(input.contentMd());
        String thumbnail = rendered.imageUrls().isEmpty() ? null : rendered.imageUrls().get(0);
        String handle = memberSummaryQuery.findByIds(Set.of(user.memberId())).get(user.memberId()).handle();

        Applied applied = transactionTemplate.execute(status -> {
            PostEditRow row = store.lockForPublish(postId)
                    .filter(r -> r.authorId() == user.memberId())
                    .orElseThrow(NotFoundException::new);
            EditingContent current = draftService.current(row);
            if (current.version() != input.baseVersion()) {
                throw new EditConflictException(current.title(), current.contentMd(), current.version(), current.savedAt());
            }
            tagService.replace(postId, input.tags());
            Instant now = clock.instant();
            long nextVersion = current.version() + 1;
            PostEditStore.PublishedTimes times = store.applyPublish(postId, input.title(), input.contentMd(),
                    rendered.html(), rendered.excerpt().isEmpty() ? null : rendered.excerpt(), thumbnail,
                    input.visibility(), rendered.renderVersion(), nextVersion, now);
            store.deleteWorkingCopy(postId);
            if (row.status() == PostStatus.DRAFT) {
                events.publishEvent(new PostPublished(postId, user.memberId(), input.visibility(), times.firstPublicAt()));
            } else {
                events.publishEvent(new PostEdited(postId, user.memberId(), input.visibility()));
            }
            PublishResult result = new PublishResult("/@" + handle + "/posts/" + postId, times.publishedAt(),
                    times.firstPublicAt(), times.editedAt(), nextVersion);
            return new Applied(result, current.version());
        });

        // 커밋 후 ⑨: 확인한 버전 이하의 버퍼만 지운다(실패해도 발행 결과는 그대로 — 남은 옛 버퍼는 버전 규칙으로 무시됨)
        if (circuit.allowsRedis()) {
            try {
                buffer.evictUpTo(postId, applied.checkedVersion());
            } catch (DataAccessException e) {
                circuit.recordFailure();
                log.warn("autosave buffer cleanup after publish failed for post {}: {}", postId, e.getMessage());
            }
        }
        return applied.result();
    }
}

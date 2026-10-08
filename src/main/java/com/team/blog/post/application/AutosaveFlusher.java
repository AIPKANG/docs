package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.infra.PostEditStore;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 버퍼 → PostgreSQL 영구 반영(04 §2-4). 글 하나 단위로, 반영 작업과 수동 저장이 함께 쓴다.
 * 버퍼 Hash는 지우지 않는다 — 반영하는 사이에 새 버전이 들어왔을 수 있다. 정리는 TTL·발행·변경 취소가 맡는다.
 */
@Component
public class AutosaveFlusher {

    private final AutosaveBuffer buffer;
    private final PostEditStore store;
    private final Clock clock;
    private final PostImageLinker imageLinker;

    public AutosaveFlusher(AutosaveBuffer buffer, PostEditStore store, Clock clock, PostImageLinker imageLinker) {
        this.buffer = buffer;
        this.store = store;
        this.clock = clock;
        this.imageLinker = imageLinker;
    }

    /** 반영한 버전(버퍼가 비었으면 빈 값). 글이 없거나 휴지통이면 반영 없이 대기 목록에서만 뺀다. */
    public Optional<Long> flushOne(long postId) {
        Optional<BufferedContent> content = buffer.read(postId);
        if (content.isEmpty()) {
            buffer.markFlushed(postId, Long.MAX_VALUE);
            return Optional.empty();
        }
        BufferedContent c = content.get();
        store.findActive(postId).ifPresent(row -> {
            if (row.status() == PostStatus.DRAFT) {
                store.flushDraft(postId, c.title(), c.contentMd(), c.version(), clock.instant());
            } else {
                store.flushWorkingCopy(postId, c.title(), c.contentMd(), c.version(), clock.instant());
            }
            imageLinker.relink(row, c.contentMd()); // 008: 본문 사진 연결
        });
        buffer.markFlushed(postId, c.version());
        return Optional.of(c.version());
    }

    /** 대기 목록에서 최대 {@code batchSize}개를 반영한다. 글 하나의 실패는 다음 글을 막지 않는다(다음 실행에서 재시도). */
    public int flushBatch(int batchSize) {
        int flushed = 0;
        for (Long postId : buffer.dirtyBatch(batchSize)) {
            try {
                if (flushOne(postId).isPresent()) {
                    flushed++;
                }
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(AutosaveFlusher.class)
                        .warn("autosave flush failed for post {}: {}", postId, e.getMessage());
            }
        }
        return flushed;
    }
}

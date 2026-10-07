package com.team.blog.post.application;

import com.team.blog.post.infra.PostEditStore;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 빈 임시글 정리(FR-025, research R-9): 제목·본문이 공백뿐이고 만든 지·마지막 수정 후 모두 {@code min-age}(24시간)가 지났으며
 * 서버 버퍼에도 내용이 없는 임시글을 완전 삭제한다. 버퍼 존재를 확인할 수 없으면(Redis 장애) 지우지 않는다.
 */
@Service
public class EmptyDraftCleanupService {

    private static final Logger log = LoggerFactory.getLogger(EmptyDraftCleanupService.class);

    private final PostEditStore store;
    private final AutosaveBuffer buffer;
    private final PostProperties properties;
    private final Clock clock;

    public EmptyDraftCleanupService(PostEditStore store, AutosaveBuffer buffer, PostProperties properties, Clock clock) {
        this.store = store;
        this.buffer = buffer;
        this.properties = properties;
        this.clock = clock;
    }

    /** 지운 글 수. */
    public int runOnce() {
        PostProperties.EmptyDraftCleanup config = properties.emptyDraftCleanup();
        Instant cutoff = clock.instant().minus(config.minAge());
        int deleted = 0;
        for (Long postId : store.emptyDraftCandidates(cutoff, config.batchSize())) {
            try {
                if (buffer.exists(postId)) {
                    continue;
                }
            } catch (DataAccessException e) {
                log.warn("empty draft cleanup skipped: autosave buffer unavailable ({})", e.getMessage());
                return deleted;
            }
            deleted += store.deleteEmptyDraft(postId, cutoff);
        }
        return deleted;
    }
}

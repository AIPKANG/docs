package com.team.blog.post.application;

import com.team.blog.post.infra.PostEditStore;
import org.springframework.stereotype.Component;

/** "수정 중"인지(작업본 또는 발행본보다 새 버퍼, 004 FR-023). 상세·목록이 함께 쓴다. */
@Component
public class PostEditFacts {

    private final PostEditStore store;
    private final PostDraftService draftService;

    public PostEditFacts(PostEditStore store, PostDraftService draftService) {
        this.store = store;
        this.draftService = draftService;
    }

    public boolean isEditing(long postId) {
        return store.findActive(postId).map(draftService::isEditing).orElse(false);
    }
}

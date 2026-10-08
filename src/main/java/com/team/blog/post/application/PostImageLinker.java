package com.team.blog.post.application;

import com.team.blog.media.application.PostImageService;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.markdown.ContentRenderer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 저장·반영·변경 취소 때 본문의 사진 연결을 맞춘다(008 FR-019, research R-3). 임시글은 본문, 발행 글은 발행본 + 작업본의 사진
 * (독자가 보는 사진을 끊지 않음). 실패해도 저장은 그대로 — 다음 저장·반영 때 다시 맞춘다(헌법 V).
 */
@Component
public class PostImageLinker {

    private static final Logger log = LoggerFactory.getLogger(PostImageLinker.class);

    private final PostImageService postImageService;
    private final ContentRenderer renderer;
    private final Clock clock;

    public PostImageLinker(PostImageService postImageService, ContentRenderer renderer, Clock clock) {
        this.postImageService = postImageService;
        this.renderer = renderer;
        this.clock = clock;
    }

    /** @param editingContent 방금 저장한 내용(발행 글이면 작업본, 변경 취소면 {@code null}) */
    public void relink(PostEditRow row, String editingContent) {
        try {
            Set<String> urls = new LinkedHashSet<>();
            if (row.status() == PostStatus.PUBLISHED) {
                urls.addAll(renderer.imageUrls(row.contentMd()));
            }
            if (editingContent != null) {
                urls.addAll(renderer.imageUrls(editingContent));
            }
            List<String> list = new ArrayList<>(urls);
            postImageService.sync(row.id(), row.authorId(), list, clock.instant());
        } catch (RuntimeException e) {
            log.warn("post image relink failed for post {}: {}", row.id(), e.getMessage());
        }
    }
}

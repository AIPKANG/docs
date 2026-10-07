package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import java.time.Instant;
import java.util.List;

/** 글 상세 한 건(010이 조회수·좋아요·댓글을 더한다). {@code contentHtml}은 007 렌더러가 정화한 결과뿐이다. */
public record PostView(long id, long authorId, PostStatus status, String visibility, String title, String contentHtml,
                       Instant publishedAt, Instant editedAt, List<String> tags, boolean editing) {

    public boolean isDraft() {
        return status == PostStatus.DRAFT;
    }

    public boolean isPrivate() {
        return "PRIVATE".equals(visibility);
    }
}

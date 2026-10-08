package com.team.blog.post.application;

import com.team.blog.post.application.visibility.PostFacts;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.CurrentUser;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 다른 모듈(댓글·좋아요·신고)이 "이 글을 읽을 수 있나"를 묻는 공개 Service(헌법 I·III). 판정은 006 {@link PostAccessPolicy}
 * 한 곳이고, 반응 대상은 발행된 글만이다(임시글은 작성자에게도 반응 대상이 아님). 볼 수 없으면 없는 글과 같은 404.
 */
@Service
public class PostReadAccess {

    /** 반응 대상 글 요약. */
    public record ReadablePost(long id, long authorId, String visibility, boolean hidden) {

        public boolean isPublic() {
            return "PUBLIC".equals(visibility);
        }
    }

    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;

    public PostReadAccess(JdbcTemplate jdbc, PostAccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    /** 발행되고 휴지통에 없으며 {@code viewer}가 읽을 수 있는 글. 아니면 404. */
    public ReadablePost requireReadable(Optional<CurrentUser> viewer, long postId) {
        record Row(long authorId, PostStatus status, String visibility, boolean authorWithdrawn, boolean hidden) {
        }
        Row row = jdbc.query("""
                SELECT p.author_id, p.status, p.visibility, m.withdrawn_at IS NOT NULL AS withdrawn, p.hidden_at IS NOT NULL AS hidden
                FROM post p JOIN member m ON m.id = p.author_id WHERE p.id = ? AND p.deleted_at IS NULL
                """, (rs, n) -> new Row(rs.getLong("author_id"), PostStatus.valueOf(rs.getString("status")),
                        rs.getString("visibility"), rs.getBoolean("withdrawn"), rs.getBoolean("hidden")), postId)
                .stream().findFirst().orElseThrow(NotFoundException::new);
        if (row.status() != PostStatus.PUBLISHED
                || !accessPolicy.canRead(viewer, new PostFacts(postId, row.authorId(), row.status(), row.visibility(),
                        row.authorWithdrawn(), row.hidden()))) {
            throw new NotFoundException();
        }
        return new ReadablePost(postId, row.authorId(), row.visibility(), row.hidden());
    }

    /** 쓰기(댓글·좋아요·신고) 대상: 읽을 수 있고 관리자가 숨기지 않은 글(010 FR-032). */
    public ReadablePost requireWritableTarget(Optional<CurrentUser> viewer, long postId) {
        ReadablePost post = requireReadable(viewer, postId);
        if (post.hidden()) {
            throw new NotFoundException();
        }
        return post;
    }
}

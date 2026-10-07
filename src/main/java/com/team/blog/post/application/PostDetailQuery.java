package com.team.blog.post.application;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.tag.application.PostTagService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 글 상세 조회({@code /@{handle}/posts/{id}}, 005 R-8). 주소의 블로그 주인과 글 작성자가 다르거나 볼 수 없으면 404 — 없는 글과
 * 구별하지 않는다. 독자에게는 마지막 발행본만(작업본·버퍼는 보이지 않음, FR-002).
 */
@Service
public class PostDetailQuery {

    private final BlogOwnerResolver blogOwnerResolver;
    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;
    private final PostTagService tagService;
    private final PostEditFacts editFacts;

    public PostDetailQuery(BlogOwnerResolver blogOwnerResolver, JdbcTemplate jdbc, PostAccessPolicy accessPolicy,
                           PostTagService tagService, PostEditFacts editFacts) {
        this.blogOwnerResolver = blogOwnerResolver;
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.tagService = tagService;
        this.editFacts = editFacts;
    }

    public record Detail(BlogOwner owner, PostView post, boolean viewerIsAuthor) {
    }

    public Detail find(Optional<CurrentUser> viewer, String handle, long postId) {
        BlogOwner owner = blogOwnerResolver.resolve(handle).orElseThrow(NotFoundException::new);
        record Row(long id, long authorId, PostStatus status, String visibility, String title, String html,
                   Instant publishedAt, Instant editedAt) {
        }
        Row row = jdbc.query("""
                SELECT id, author_id, status, visibility, title, content_html, published_at, edited_at
                FROM post WHERE id = ? AND author_id = ? AND deleted_at IS NULL
                """, (rs, n) -> new Row(rs.getLong("id"), rs.getLong("author_id"), PostStatus.valueOf(rs.getString("status")),
                        rs.getString("visibility"), rs.getString("title"), rs.getString("content_html"),
                        instant(rs.getTimestamp("published_at")), instant(rs.getTimestamp("edited_at"))),
                postId, owner.memberId()).stream().findFirst().orElseThrow(NotFoundException::new);
        if (!accessPolicy.canRead(viewer, row.authorId(), row.status(), row.visibility())) {
            throw new NotFoundException();
        }
        boolean isAuthor = viewer.map(v -> v.memberId() == row.authorId()).orElse(false);
        List<String> tags = row.status() == PostStatus.PUBLISHED ? tagService.tagsOf(postId) : List.of();
        boolean editing = isAuthor && row.status() == PostStatus.PUBLISHED && editFacts.isEditing(postId);
        PostView view = new PostView(row.id(), row.authorId(), row.status(), row.visibility(), row.title(), row.html(),
                row.publishedAt(), row.editedAt(), tags, editing);
        return new Detail(owner, view, isAuthor);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

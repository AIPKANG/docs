package com.team.blog.post.application;

import com.team.blog.post.application.visibility.PostFacts;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.tag.application.PostTagService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 글 상세 조회(010 R-1·R-2). 글 + 작성자 JOIN 1번(본문 원문 제외), 태그 1번, 작성자일 때 고치는 중 여부.
 * 볼 수 있는지는 006 {@link PostAccessPolicy} 한 곳에서 판정하고, 볼 수 없으면 없는 글과 똑같이 빈 값이다.
 */
@Service
public class PostDetailQuery {

    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;
    private final PostTagService tagService;
    private final PostEditStoreFacade editFacade;

    public PostDetailQuery(JdbcTemplate jdbc, PostAccessPolicy accessPolicy, PostTagService tagService,
                           PostEditStoreFacade editFacade) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.tagService = tagService;
        this.editFacade = editFacade;
    }

    /** 볼 수 있는 글(휴지통 밖). 볼 수 없거나 없으면 빈 값. */
    public Optional<PostDetail> find(Optional<CurrentUser> viewer, long postId) {
        record Row(PostDetail detail, boolean authorWithdrawn) {
        }
        Optional<Row> row = jdbc.query("""
                SELECT p.id, p.author_id, p.status, p.visibility, p.title, p.content_html, p.excerpt, p.published_at,
                       p.first_public_at, p.edited_at, p.view_count, p.like_count, p.comment_count,
                       m.handle, m.nickname, m.profile_image_url, m.bio, m.withdrawn_at,
                       CASE WHEN p.hidden_at IS NULL THEN NULL ELSE COALESCE(p.hidden_reason, 'OTHER') END AS hidden_reason
                FROM post p JOIN member m ON m.id = p.author_id
                WHERE p.id = ? AND p.deleted_at IS NULL
                """, (rs, n) -> new Row(map(rs), rs.getTimestamp("withdrawn_at") != null), postId).stream().findFirst();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        PostDetail d = row.get().detail();
        PostFacts facts = new PostFacts(d.id(), d.authorId(), d.status(), d.visibility(), row.get().authorWithdrawn(),
                d.hidden());
        if (!accessPolicy.canRead(viewer, facts)) {
            return Optional.empty();
        }
        boolean isAuthor = viewer.map(v -> v.memberId() == d.authorId()).orElse(false);
        List<String> tags = d.status() == PostStatus.PUBLISHED ? tagService.tagsOf(postId) : List.of();
        Optional<Instant> editingSavedAt = isAuthor && d.status() == PostStatus.PUBLISHED
                ? editFacade.editingSavedAt(postId) : Optional.empty();
        return Optional.of(new PostDetail(d.id(), d.authorId(), d.status(), d.visibility(), d.title(), d.contentHtml(),
                d.excerpt(), d.publishedAt(), d.firstPublicAt(), d.editedAt(), d.viewCount(), d.likeCount(),
                d.commentCount(), tags, d.author(), editingSavedAt.isPresent(), editingSavedAt.orElse(null), d.hiddenReason()));
    }

    private static PostDetail map(ResultSet rs) throws SQLException {
        return new PostDetail(rs.getLong("id"), rs.getLong("author_id"), PostStatus.valueOf(rs.getString("status")),
                rs.getString("visibility"), rs.getString("title"), rs.getString("content_html"), rs.getString("excerpt"),
                instant(rs.getTimestamp("published_at")), instant(rs.getTimestamp("first_public_at")),
                instant(rs.getTimestamp("edited_at")), rs.getLong("view_count"), rs.getInt("like_count"),
                rs.getInt("comment_count"), List.of(),
                new PostDetail.Author(rs.getString("handle"), rs.getString("nickname"), rs.getString("profile_image_url"),
                        rs.getString("bio")),
                false, null, rs.getString("hidden_reason"));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

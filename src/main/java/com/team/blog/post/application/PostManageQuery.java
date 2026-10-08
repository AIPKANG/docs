package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 내 글 관리(011 R-1, 41 §5). 대상은 로그인 정보로만 정한다. 탭마다 20개(21개 조회로 다음 판단), 본문 칸 없이 SQL 1번
 * (작업본 유무는 LEFT JOIN). 정렬·커서: 임시·발행 {@code (updated_at, id)}, 휴지통 {@code (deleted_at, id)}.
 */
@Service
public class PostManageQuery {

    private final AccountGuard accountGuard;
    private final JdbcTemplate jdbc;
    private final PostProperties properties;

    public PostManageQuery(AccountGuard accountGuard, JdbcTemplate jdbc, PostProperties properties) {
        this.accountGuard = accountGuard;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public ManagePage page(Optional<CurrentUser> currentUser, ManageTab tab, String visibility, String cursor) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        Optional<FeedCursor> after = FeedCursor.decode(cursor);
        int size = properties.managePageSize();
        String sortColumn = tab == ManageTab.TRASH ? "p.deleted_at" : "p.updated_at";
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.status, p.visibility, p.updated_at, p.published_at, p.edited_at, p.deleted_at,
                       p.view_count, p.like_count, p.comment_count, p.hidden_at, p.edit_version,
                       d.edit_version AS d_version, m.handle
                FROM post p JOIN member m ON m.id = p.author_id LEFT JOIN post_draft d ON d.post_id = p.id
                WHERE p.author_id = ?""");
        List<Object> args = new ArrayList<>();
        args.add(user.memberId());
        switch (tab) {
            case DRAFTS -> sql.append(" AND p.status = 'DRAFT' AND p.deleted_at IS NULL");
            case PUBLISHED -> {
                sql.append(" AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL");
                String v = visibility == null ? null : visibility.toUpperCase(java.util.Locale.ROOT);
                if ("PUBLIC".equals(v) || "PRIVATE".equals(v) || "FRIENDS".equals(v) || "LINK".equals(v)) { // FRIENDS·LINK: 025·029 강성찬 개인 확장
                    sql.append(" AND p.visibility = ?");
                    args.add(v);
                }
            }
            case TRASH -> sql.append(" AND p.deleted_at IS NOT NULL");
        }
        if (after.isPresent()) {
            sql.append(" AND (").append(sortColumn).append(", p.id) < (?, ?)");
            args.add(Timestamp.from(after.get().firstPublicAt()));
            args.add(after.get().id());
        }
        sql.append(" ORDER BY ").append(sortColumn).append(" DESC, p.id DESC LIMIT ").append(size + 1);
        List<ManageRow> rows = jdbc.query(sql.toString(), (rs, n) -> row(rs), args.toArray());
        String next = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            ManageRow last = rows.get(size - 1);
            next = new FeedCursor(tab == ManageTab.TRASH ? last.deletedAt() : last.updatedAt(), last.id()).encode();
        }
        Map<String, Long> counts = after.isEmpty() ? counts(user.memberId()) : null;
        return new ManagePage(List.copyOf(rows), next, counts);
    }

    /** 탭별 글 수(GROUP BY 1번). */
    public Map<String, Long> counts(long memberId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("drafts", 0L);
        counts.put("published", 0L);
        counts.put("trash", 0L);
        jdbc.query("""
                SELECT CASE WHEN deleted_at IS NOT NULL THEN 'trash' WHEN status = 'DRAFT' THEN 'drafts' ELSE 'published' END AS tab,
                       count(*) AS n
                FROM post WHERE author_id = ? GROUP BY 1
                """, rs -> {
                    counts.put(rs.getString("tab"), rs.getLong("n"));
                }, memberId);
        return counts;
    }

    private ManageRow row(ResultSet rs) throws SQLException {
        PostStatus status = PostStatus.valueOf(rs.getString("status"));
        long draftVersion = rs.getLong("d_version");
        boolean editing = status == PostStatus.PUBLISHED && !rs.wasNull() && draftVersion > rs.getLong("edit_version");
        Instant deletedAt = instant(rs.getTimestamp("deleted_at"));
        long id = rs.getLong("id");
        return new ManageRow(id, rs.getString("title"), status, rs.getString("visibility"), editing,
                rs.getTimestamp("hidden_at") != null, instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("published_at")), instant(rs.getTimestamp("edited_at")), deletedAt,
                deletedAt == null ? null : deletedAt.plus(properties.trash().retention()),
                rs.getLong("view_count"), rs.getInt("like_count"), rs.getInt("comment_count"),
                "/@" + rs.getString("handle") + "/posts/" + id);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

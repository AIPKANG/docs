package com.team.blog.interaction.application;

import com.team.blog.post.application.FeedCursor;
import com.team.blog.post.application.PostReadAccess;
import com.team.blog.shared.security.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 댓글 조회(014 R-3, 21 §6). 최상위 21개 + 그 최상위들의 처음 답글 3개(답글 수 포함) — 페이지당 SQL 2번. 오래된 순, 커서
 * {@code (created_at, id)}. 상태 판정: 작성자 탈퇴 → 삭제된 자리 → 숨김 → 정상. 탈퇴·삭제·남이 보는 숨김은 내용·작성자를 보내지 않는다.
 */
@Service
public class CommentQuery {

    private static final String SELECT = """
            SELECT c.id, c.post_id, c.parent_id, c.author_id, c.content, c.created_at, c.updated_at, c.deleted_at, c.hidden_at,
                   m.handle, m.nickname, m.profile_image_url, m.withdrawn_at,
                   rt.handle AS rt_handle, rt.nickname AS rt_nickname, rt.withdrawn_at AS rt_withdrawn
            FROM comment c JOIN member m ON m.id = c.author_id LEFT JOIN member rt ON rt.id = c.reply_to_member_id
            """;

    private final JdbcTemplate jdbc;
    private final PostReadAccess postReadAccess;
    private final CommentProperties properties;

    public CommentQuery(JdbcTemplate jdbc, PostReadAccess postReadAccess, CommentProperties properties) {
        this.jdbc = jdbc;
        this.postReadAccess = postReadAccess;
        this.properties = properties;
    }

    private record Row(long id, long postId, Long parentId, long authorId, String content, Instant createdAt,
                       Instant updatedAt, boolean deleted, boolean hidden, String handle, String nickname, String image,
                       boolean authorWithdrawn, String rtHandle, String rtNickname, boolean rtWithdrawn) {
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getLong("id"), rs.getLong("post_id"), (Long) rs.getObject("parent_id"), rs.getLong("author_id"),
                rs.getString("content"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("deleted_at") != null, rs.getTimestamp("hidden_at") != null, rs.getString("handle"),
                rs.getString("nickname"), rs.getString("profile_image_url"), rs.getTimestamp("withdrawn_at") != null,
                rs.getString("rt_handle"), rs.getString("rt_nickname"), rs.getTimestamp("rt_withdrawn") != null);
    }

    /** 글의 댓글 첫 페이지 또는 이어 보기({@code cursor})·이전 보기({@code before})·특정 댓글부터({@code around}). */
    public CommentPage page(Optional<CurrentUser> viewer, long postId, String cursor, String before, Long around) {
        PostReadAccess.ReadablePost post = postReadAccess.requireReadable(viewer, postId);
        return pageOfReadable(viewer, postId, post.authorId(), cursor, before, around);
    }

    /** 글 상세처럼 이미 읽기 판정을 마친 화면용(다시 판정하지 않음, 010 FR-027). */
    public CommentPage pageOfReadable(Optional<CurrentUser> viewer, long postId, long postAuthorId, String cursor,
                                      String before, Long around) {
        int size = properties.pageSize();
        String rootsWhere = SELECT + " WHERE c.post_id = ? AND c.parent_id IS NULL";
        Optional<Row> target = around == null ? Optional.empty() : findAroundTarget(postId, around);
        Row targetRoot = target.map(t -> t.parentId() == null ? t : rowById(t.parentId()).orElse(null)).orElse(null);
        List<Row> roots;
        boolean hasNext;
        String prevCursor = null;
        if (targetRoot != null) {
            List<Row> rows = jdbc.query(rootsWhere + " AND (c.created_at, c.id) >= (?, ?) ORDER BY c.created_at, c.id LIMIT "
                    + (size + 1), (rs, n) -> row(rs), postId, Timestamp.from(targetRoot.createdAt()), targetRoot.id());
            hasNext = rows.size() > size;
            roots = rows.subList(0, Math.min(size, rows.size()));
            if (hasBefore(postId, targetRoot)) {
                prevCursor = new FeedCursor(targetRoot.createdAt(), targetRoot.id()).encode();
            }
        } else if (before != null && !before.isEmpty()) {
            FeedCursor b = FeedCursor.decode(before).orElseThrow();
            List<Row> back = jdbc.query(rootsWhere + " AND (c.created_at, c.id) < (?, ?) ORDER BY c.created_at DESC, c.id DESC LIMIT "
                    + (size + 1), (rs, n) -> row(rs), postId, Timestamp.from(b.firstPublicAt()), b.id());
            boolean more = back.size() > size;
            List<Row> page = new ArrayList<>(back.subList(0, Math.min(size, back.size())));
            Collections.reverse(page);
            roots = page;
            hasNext = true; // 이전 보기는 뒤쪽 페이지에서 왔다
            if (more && !page.isEmpty()) {
                prevCursor = new FeedCursor(page.get(0).createdAt(), page.get(0).id()).encode();
            }
        } else {
            Optional<FeedCursor> c = FeedCursor.decode(cursor);
            List<Row> rows = c.isPresent()
                    ? jdbc.query(rootsWhere + " AND (c.created_at, c.id) > (?, ?) ORDER BY c.created_at, c.id LIMIT " + (size + 1),
                            (rs, n) -> row(rs), postId, Timestamp.from(c.get().firstPublicAt()), c.get().id())
                    : jdbc.query(rootsWhere + " ORDER BY c.created_at, c.id LIMIT " + (size + 1), (rs, n) -> row(rs), postId);
            hasNext = rows.size() > size;
            roots = rows.subList(0, Math.min(size, rows.size()));
        }
        String next = hasNext && !roots.isEmpty()
                ? new FeedCursor(roots.get(roots.size() - 1).createdAt(), roots.get(roots.size() - 1).id()).encode() : null;
        Long expandRoot = target.isPresent() && target.get().parentId() != null ? target.get().parentId() : null;
        Long expandTo = expandRoot == null ? null : target.get().id();
        return new CommentPage(withReplies(viewer, postAuthorId, roots, expandRoot, expandTo), next, prevCursor);
    }

    /** 답글 더 보기(4번째부터 20개씩). */
    public CommentPage replies(Optional<CurrentUser> viewer, long rootId, String cursor) {
        Row root = rowById(rootId).filter(r -> r.parentId() == null).orElseThrow(com.team.blog.shared.error.NotFoundException::new);
        PostReadAccess.ReadablePost post = postReadAccess.requireReadable(viewer, root.postId());
        int size = properties.pageSize();
        Optional<FeedCursor> c = FeedCursor.decode(cursor);
        List<Row> rows = c.isPresent()
                ? jdbc.query(SELECT + " WHERE c.parent_id = ? AND (c.created_at, c.id) > (?, ?) ORDER BY c.created_at, c.id LIMIT "
                        + (size + 1), (rs, n) -> row(rs), rootId, Timestamp.from(c.get().firstPublicAt()), c.get().id())
                : jdbc.query(SELECT + " WHERE c.parent_id = ? ORDER BY c.created_at, c.id LIMIT " + (size + 1),
                        (rs, n) -> row(rs), rootId);
        boolean hasNext = rows.size() > size;
        List<Row> page = rows.subList(0, Math.min(size, rows.size()));
        String next = hasNext ? new FeedCursor(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id()).encode() : null;
        List<CommentView> items = new ArrayList<>();
        for (Row r : page) {
            items.add(view(viewer, post.authorId(), r, null, List.of(), null));
        }
        return new CommentPage(items, next, null);
    }

    /** 방금 만들거나 고친 댓글 하나. */
    public CommentView single(Optional<CurrentUser> viewer, long commentId) {
        Row r = rowById(commentId).orElseThrow(com.team.blog.shared.error.NotFoundException::new);
        PostReadAccess.ReadablePost post = postReadAccess.requireReadable(viewer, r.postId());
        return view(viewer, post.authorId(), r, r.parentId() == null ? 0 : null, r.parentId() == null ? List.of() : null, null);
    }

    private Optional<Row> rowById(long id) {
        return jdbc.query(SELECT + " WHERE c.id = ?", (rs, n) -> row(rs), id).stream().findFirst();
    }

    /** 특정 댓글부터: 그 글의 정상 댓글일 때만(아니면 오류 없이 첫 페이지). */
    private Optional<Row> findAroundTarget(long postId, long commentId) {
        return rowById(commentId).filter(r -> r.postId() == postId && !r.deleted() && !r.hidden() && !r.authorWithdrawn());
    }

    private boolean hasBefore(long postId, Row root) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM comment WHERE post_id = ? AND parent_id IS NULL AND (created_at, id) < (?, ?)",
                Integer.class, postId, Timestamp.from(root.createdAt()), root.id());
        return n != null && n > 0;
    }

    private List<CommentView> withReplies(Optional<CurrentUser> viewer, long postAuthorId, List<Row> roots, Long expandRoot,
                                          Long expandTo) {
        if (roots.isEmpty()) {
            return List.of();
        }
        Long[] ids = roots.stream().map(Row::id).toArray(Long[]::new);
        int preview = properties.previewReplies();
        int expandRank = preview;
        if (expandRoot != null) {
            Integer rank = jdbc.queryForObject("""
                    SELECT rn FROM (SELECT id, row_number() OVER (ORDER BY created_at, id) AS rn FROM comment WHERE parent_id = ?) t
                    WHERE id = ?""", Integer.class, expandRoot, expandTo);
            expandRank = Math.max(preview, rank == null ? preview : rank);
        }
        Map<Long, List<Row>> replies = new LinkedHashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        int finalExpandRank = expandRank;
        jdbc.query(con -> {
            var ps = con.prepareStatement("""
                    SELECT * FROM (
                      SELECT c.id, c.post_id, c.parent_id, c.author_id, c.content, c.created_at, c.updated_at, c.deleted_at, c.hidden_at,
                             m.handle, m.nickname, m.profile_image_url, m.withdrawn_at,
                             rt.handle AS rt_handle, rt.nickname AS rt_nickname, rt.withdrawn_at AS rt_withdrawn,
                             row_number() OVER (PARTITION BY c.parent_id ORDER BY c.created_at, c.id) AS rn,
                             count(*) OVER (PARTITION BY c.parent_id) AS reply_count
                      FROM comment c JOIN member m ON m.id = c.author_id LEFT JOIN member rt ON rt.id = c.reply_to_member_id
                      WHERE c.parent_id = ANY(?)
                    ) t WHERE rn <= CASE WHEN parent_id = ? THEN ? ELSE ? END
                    ORDER BY parent_id, created_at, id""");
            ps.setArray(1, con.createArrayOf("bigint", ids));
            ps.setObject(2, expandRoot);
            ps.setInt(3, finalExpandRank);
            ps.setInt(4, preview);
            return ps;
        }, rs -> {
            Row r = row(rs);
            replies.computeIfAbsent(r.parentId(), k -> new ArrayList<>()).add(r);
            counts.put(r.parentId(), rs.getInt("reply_count"));
        });
        List<CommentView> out = new ArrayList<>();
        for (Row root : roots) {
            List<Row> rs = replies.getOrDefault(root.id(), List.of());
            List<CommentView> rv = new ArrayList<>();
            for (Row r : rs) {
                rv.add(view(viewer, postAuthorId, r, null, null, null));
            }
            int count = counts.getOrDefault(root.id(), 0);
            String repliesNext = count > rs.size() && !rs.isEmpty()
                    ? new FeedCursor(rs.get(rs.size() - 1).createdAt(), rs.get(rs.size() - 1).id()).encode() : null;
            out.add(view(viewer, postAuthorId, root, count, rv, repliesNext));
        }
        return out;
    }

    private CommentView view(Optional<CurrentUser> viewer, long postAuthorId, Row r, Integer replyCount,
                             List<CommentView> replies, String repliesNext) {
        long me = viewer.map(CurrentUser::memberId).orElse(-1L);
        boolean mine = r.authorId() == me;
        String state = r.authorWithdrawn() ? "WITHDRAWN_AUTHOR" : r.deleted() ? "DELETED" : r.hidden() ? "HIDDEN" : "NORMAL";
        boolean showContent = "NORMAL".equals(state) || ("HIDDEN".equals(state) && mine);
        CommentView.Author author = showContent
                ? new CommentView.Author(r.handle(), r.nickname(), r.image(), r.authorId() == postAuthorId) : null;
        CommentView.ReplyTo replyTo = null;
        if (showContent && r.rtHandle() != null) {
            replyTo = r.rtWithdrawn() ? new CommentView.ReplyTo(null, "탈퇴한 사용자")
                    : new CommentView.ReplyTo(r.rtHandle(), r.rtNickname());
        }
        boolean normal = "NORMAL".equals(state);
        boolean loggedIn = viewer.isPresent();
        return new CommentView(r.id(), r.parentId(), state, showContent ? r.content() : null, author, replyTo,
                r.createdAt(), showContent && r.updatedAt().isAfter(r.createdAt()), mine,
                normal && loggedIn, normal && mine, mine && (normal || "HIDDEN".equals(state)),
                normal && loggedIn && !mine, replyCount, replies, repliesNext);
    }
}

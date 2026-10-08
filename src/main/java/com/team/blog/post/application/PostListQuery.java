package com.team.blog.post.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 홈·개인 블로그 목록(009, 10 §4·§7). 006의 공용 목록 조건만 쓰고, 목록 한 번에 SQL 1번(글 + 작성자 JOIN, 카드 칸만)이다.
 * 9개를 보여주려고 10개를 읽어 다음 글이 있는지 판단한다. 정렬·커서는 {@code (first_public_at, id)}.
 */
@Service
public class PostListQuery {

    public static final int PAGE_SIZE = 9;

    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;

    public PostListQuery(JdbcTemplate jdbc, PostAccessPolicy accessPolicy) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
    }

    public CardPage feed(String cursor) {
        return page(null, FeedCursor.decode(cursor));
    }

    public CardPage blog(long authorId, String cursor) {
        return page(authorId, FeedCursor.decode(cursor));
    }

    /** 블로그 머리말의 공개 글 수(보는 사람 기준 — 지금은 공개 글만 있으므로 누구에게나 같다). */
    public long publicCount(long authorId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM post p JOIN member m ON m.id = p.author_id WHERE "
                + accessPolicy.publicListingCondition("p", "m") + " AND p.author_id = ?", Long.class, authorId);
        return count == null ? 0 : count;
    }

    private CardPage page(Long authorId, Optional<FeedCursor> cursor) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.excerpt, p.thumbnail_url, p.first_public_at, p.comment_count, p.like_count,
                       m.handle, m.nickname, m.profile_image_url
                FROM post p JOIN member m ON m.id = p.author_id
                WHERE """).append(' ').append(accessPolicy.publicListingCondition("p", "m"));
        List<Object> args = new ArrayList<>();
        if (authorId != null) {
            sql.append(" AND p.author_id = ?");
            args.add(authorId);
        }
        if (cursor.isPresent()) {
            sql.append(" AND (p.first_public_at, p.id) < (?, ?)");
            args.add(Timestamp.from(cursor.get().firstPublicAt()));
            args.add(cursor.get().id());
        }
        sql.append(" ORDER BY p.first_public_at DESC, p.id DESC LIMIT ").append(PAGE_SIZE + 1);
        List<PostCard> rows = jdbc.query(sql.toString(), PostListQuery::card, args.toArray());
        if (rows.size() <= PAGE_SIZE) {
            return new CardPage(rows, null);
        }
        List<PostCard> items = rows.subList(0, PAGE_SIZE);
        PostCard last = items.get(PAGE_SIZE - 1);
        return new CardPage(List.copyOf(items), new FeedCursor(last.firstPublicAt(), last.id()).encode());
    }

    private static PostCard card(ResultSet rs, int rowNum) throws SQLException {
        String handle = rs.getString("handle");
        long id = rs.getLong("id");
        return new PostCard(id, "/@" + handle + "/posts/" + id, rs.getString("title"),
                rs.getString("excerpt") == null ? "" : rs.getString("excerpt"), rs.getString("thumbnail_url"),
                rs.getTimestamp("first_public_at").toInstant(), rs.getInt("comment_count"), rs.getInt("like_count"),
                new PostCard.Author(handle, rs.getString("nickname"), rs.getString("profile_image_url")));
    }
}

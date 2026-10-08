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
 * 홈·개인 블로그 목록(009, 10 §4·§7). 006의 공용 목록 조건만 쓰고, 목록 한 번에 SQL 1번(글 + 작성자 JOIN, 카드 칸만, 카드 머리의 첫 태그는 하위 조회)이다.
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
        return page(null, null, null, FeedCursor.decode(cursor), false);
    }

    /** 018 팔로잉 피드: 그 회원이 팔로우한 사람의 글(같은 공용 조건·정렬·커서). */
    public CardPage followingFeed(long followerId, String cursor) {
        return page(null, null, followerId, FeedCursor.decode(cursor), false);
    }

    public CardPage blog(long authorId, String cursor) {
        return blog(authorId, cursor, false);
    }

    /** {@code withFriends}: 보는 사람이 글쓴이의 친구면 친구 공개 글도(025, 강성찬 개인 확장). */
    public CardPage blog(long authorId, String cursor, boolean withFriends) {
        return page(authorId, null, null, FeedCursor.decode(cursor), withFriends);
    }

    /** 013: 블로그 태그 필터. */
    public CardPage blogByTag(long authorId, long tagId, String cursor) {
        return blogByTag(authorId, tagId, cursor, false);
    }

    public CardPage blogByTag(long authorId, long tagId, String cursor, boolean withFriends) {
        return page(authorId, tagId, null, FeedCursor.decode(cursor), withFriends);
    }

    /** 013: 태그별 글 목록(공개 글만). */
    public CardPage byTag(long tagId, String cursor) {
        return page(null, tagId, null, FeedCursor.decode(cursor), false);
    }

    /** 블로그 머리말의 공개 글 수(보는 사람 기준 — 지금은 공개 글만 있으므로 누구에게나 같다). */
    public long publicCount(long authorId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM post p JOIN member m ON m.id = p.author_id WHERE "
                + accessPolicy.publicListingCondition("p", "m") + " AND p.author_id = ?", Long.class, authorId);
        return count == null ? 0 : count;
    }

    private CardPage page(Long authorId, Long tagId, Long followerId, Optional<FeedCursor> cursor, boolean withFriends) {
        // 친구가 보는 블로그: 친구 공개 글에는 최초 공개 시각이 없으므로 발행 시각으로 대신한다(025 research R-2)
        String at = withFriends ? "COALESCE(p.first_public_at, p.published_at)" : "p.first_public_at";
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.excerpt, p.thumbnail_url, """ + at + """
                 AS first_public_at, p.comment_count, p.like_count,
                       m.handle, m.nickname, m.profile_image_url,
                       (SELECT t.name FROM post_tag pt JOIN tag t ON t.id = pt.tag_id
                        WHERE pt.post_id = p.id ORDER BY pt.position LIMIT 1) AS first_tag
                FROM post p JOIN member m ON m.id = p.author_id
                WHERE """).append(' ').append(withFriends ? accessPolicy.friendBlogCondition("p", "m")
                : accessPolicy.publicListingCondition("p", "m"));
        List<Object> args = new ArrayList<>();
        if (authorId != null) {
            sql.append(" AND p.author_id = ?");
            args.add(authorId);
        }
        if (followerId != null) {
            sql.append(" AND p.author_id IN (SELECT f.followee_id FROM follow f WHERE f.follower_id = ?)");
            args.add(followerId);
        }
        if (tagId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM post_tag pt WHERE pt.post_id = p.id AND pt.tag_id = ?)");
            args.add(tagId);
        }
        if (cursor.isPresent()) {
            sql.append(" AND (").append(at).append(", p.id) < (?, ?)");
            args.add(Timestamp.from(cursor.get().firstPublicAt()));
            args.add(cursor.get().id());
        }
        sql.append(" ORDER BY ").append(at).append(" DESC, p.id DESC LIMIT ").append(PAGE_SIZE + 1);
        List<PostCard> rows = jdbc.query(sql.toString(), PostListQuery::card, args.toArray());
        if (rows.size() <= PAGE_SIZE) {
            return new CardPage(rows, null);
        }
        List<PostCard> items = rows.subList(0, PAGE_SIZE);
        PostCard last = items.get(PAGE_SIZE - 1);
        return new CardPage(List.copyOf(items), new FeedCursor(last.firstPublicAt(), last.id()).encode());
    }

    /**
     * 019 트렌딩: 주어진 글 중 지금 공용 목록 조건(+관리자 숨김 아님)을 만족하는 카드만, SQL 1번. 순서는 호출자가 정한다.
     */
    public java.util.Map<Long, PostCard> cardsByIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        List<PostCard> rows = jdbc.query("""
                SELECT p.id, p.title, p.excerpt, p.thumbnail_url, p.first_public_at, p.comment_count, p.like_count,
                       m.handle, m.nickname, m.profile_image_url,
                       (SELECT t.name FROM post_tag pt JOIN tag t ON t.id = pt.tag_id
                        WHERE pt.post_id = p.id ORDER BY pt.position LIMIT 1) AS first_tag
                FROM post p JOIN member m ON m.id = p.author_id
                WHERE """ + " " + accessPolicy.publicListingCondition("p", "m") + " AND p.hidden_at IS NULL AND p.id = ANY (?)",
                PostListQuery::card, (Object) ids.toArray(new Long[0]));
        java.util.Map<Long, PostCard> result = new java.util.HashMap<>();
        rows.forEach(c -> result.put(c.id(), c));
        return result;
    }

    private static PostCard card(ResultSet rs, int rowNum) throws SQLException {
        String handle = rs.getString("handle");
        long id = rs.getLong("id");
        return new PostCard(id, "/@" + handle + "/posts/" + id, rs.getString("title"),
                rs.getString("excerpt") == null ? "" : rs.getString("excerpt"), rs.getString("thumbnail_url"),
                rs.getTimestamp("first_public_at").toInstant(), rs.getInt("comment_count"), rs.getInt("like_count"),
                new PostCard.Author(handle, rs.getString("nickname"), rs.getString("profile_image_url")), rs.getString("first_tag"));
    }
}

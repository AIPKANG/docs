package com.team.blog.interaction.application;

import com.team.blog.post.application.FeedCursor;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 팔로워·팔로잉 수와 목록(018 FR-008~FR-010). 탈퇴 유예 회원은 수·목록에서 빠진다(관계는 남김). 목록은 쿼리 1번(회원 JOIN +
 * 보는 사람의 팔로우 여부 EXISTS), 최근 팔로우 순 {@code (follow.created_at, member.id)}, 20개씩 커서.
 */
@Service
public class FollowQuery {

    public record Counts(long followers, long following) {
    }

    public record FollowMember(String handle, String nickname, String profileImageUrl, String bio, boolean followedByMe,
                               boolean me) {

        public com.team.blog.account.application.ProfileAvatar avatar() {
            return new com.team.blog.account.application.ProfileAvatar(handle, nickname, profileImageUrl);
        }
    }

    public record FollowPage(List<FollowMember> items, String nextCursor) {
    }

    private final JdbcTemplate jdbc;
    private final FollowProperties properties;

    public FollowQuery(JdbcTemplate jdbc, FollowProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public Counts counts(long memberId) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM follow f JOIN member m ON m.id = f.follower_id
                        WHERE f.followee_id = ? AND m.withdrawn_at IS NULL) AS followers,
                       (SELECT count(*) FROM follow f JOIN member m ON m.id = f.followee_id
                        WHERE f.follower_id = ? AND m.withdrawn_at IS NULL) AS following
                """, (rs, n) -> new Counts(rs.getLong("followers"), rs.getLong("following")), memberId, memberId);
    }

    public boolean isFollowing(long followerId, long followeeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM follow WHERE follower_id = ? AND followee_id = ?)", Boolean.class,
                followerId, followeeId));
    }

    /** @param followers true면 그 회원의 팔로워, false면 그 회원이 팔로우한 사람 */
    public FollowPage page(long memberId, boolean followers, Long viewerId, String cursor) {
        Optional<FeedCursor> after = FeedCursor.decode(cursor);
        String self = followers ? "followee_id" : "follower_id";
        String other = followers ? "follower_id" : "followee_id";
        List<Object> args = new ArrayList<>();
        args.add(viewerId == null ? -1L : viewerId);
        args.add(memberId);
        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.handle, m.nickname, m.profile_image_url, m.bio, f.created_at,
                       EXISTS (SELECT 1 FROM follow v WHERE v.follower_id = ? AND v.followee_id = m.id) AS followed_by_me
                FROM follow f JOIN member m ON m.id = f.""").append(other).append("""

                WHERE f.""").append(self).append(" = ? AND m.withdrawn_at IS NULL");
        if (after.isPresent()) {
            sql.append(" AND (f.created_at, m.id) < (?, ?)");
            args.add(Timestamp.from(after.get().firstPublicAt()));
            args.add(after.get().id());
        }
        int size = properties.pageSize();
        sql.append(" ORDER BY f.created_at DESC, m.id DESC LIMIT ").append(size + 1);
        record Row(FollowMember member, java.time.Instant at, long id) {
        }
        List<Row> rows = jdbc.query(sql.toString(), (rs, n) -> new Row(new FollowMember(rs.getString("handle"),
                rs.getString("nickname"), rs.getString("profile_image_url"), firstLine(rs.getString("bio")),
                rs.getBoolean("followed_by_me"), viewerId != null && rs.getLong("id") == viewerId),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("id")), args.toArray());
        String next = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            Row last = rows.get(size - 1);
            next = new FeedCursor(last.at(), last.id()).encode();
        }
        return new FollowPage(rows.stream().map(Row::member).toList(), next);
    }

    private static String firstLine(String bio) {
        if (bio == null || bio.isBlank()) {
            return null;
        }
        return bio.strip().split("\\R", 2)[0];
    }
}

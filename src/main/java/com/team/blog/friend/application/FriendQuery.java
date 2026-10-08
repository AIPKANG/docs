package com.team.blog.friend.application;

import com.team.blog.account.application.ProfileAvatar;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 친구 관계 읽기(025). 한 쌍은 항상 (작은 id, 큰 id) 한 행이다(06 §6-1). */
@Service
public class FriendQuery {

    /** 보는 사람 기준 상태: 없음 / 내가 보낸 요청 / 받은 요청 / 친구. */
    public enum Status { NONE, SENT, RECEIVED, FRIENDS }

    public record Person(long memberId, String handle, String nickname, String profileImageUrl) {

        public ProfileAvatar avatar() {
            return new ProfileAvatar(handle, nickname, profileImageUrl);
        }
    }

    private final JdbcTemplate jdbc;

    public FriendQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean areFriends(long a, long b) {
        if (a == b) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM friendship WHERE member_a_id = LEAST(?, ?) AND member_b_id = GREATEST(?, ?)
                               AND status = 'ACCEPTED')
                """, Boolean.class, a, b, a, b));
    }

    public Status status(long viewer, long other) {
        if (viewer == other) {
            return Status.NONE;
        }
        List<Status> rows = jdbc.query("""
                SELECT status, requested_by FROM friendship WHERE member_a_id = LEAST(?, ?) AND member_b_id = GREATEST(?, ?)
                """, (rs, n) -> "ACCEPTED".equals(rs.getString("status")) ? Status.FRIENDS
                : rs.getLong("requested_by") == viewer ? Status.SENT : Status.RECEIVED, viewer, other, viewer, other);
        return rows.isEmpty() ? Status.NONE : rows.get(0);
    }

    /** 내 친구(최근에 친구가 된 순). 탈퇴 유예·익명 회원은 뺀다. */
    public List<Person> friends(long me) {
        return people(me, "f.status = 'ACCEPTED'", "f.accepted_at DESC");
    }

    /** 내가 받은 요청(최근 순). */
    public List<Person> received(long me) {
        return people(me, "f.status = 'PENDING' AND f.requested_by <> ?", "f.created_at DESC");
    }

    /** 내가 보낸 요청(최근 순). */
    public List<Person> sent(long me) {
        return people(me, "f.status = 'PENDING' AND f.requested_by = ?", "f.created_at DESC");
    }

    private List<Person> people(long me, String condition, String order) {
        String sql = """
                SELECT m.id, m.handle, m.nickname, m.profile_image_url
                FROM friendship f JOIN member m ON m.id = CASE WHEN f.member_a_id = ? THEN f.member_b_id ELSE f.member_a_id END
                WHERE (f.member_a_id = ? OR f.member_b_id = ?) AND m.withdrawn_at IS NULL AND """ + " " + condition
                + " ORDER BY " + order + " LIMIT 1000";
        Object[] args = condition.contains("?") ? new Object[] {me, me, me, me} : new Object[] {me, me, me};
        return jdbc.query(sql, (rs, n) -> new Person(rs.getLong("id"), rs.getString("handle"), rs.getString("nickname"),
                rs.getString("profile_image_url")), args);
    }
}

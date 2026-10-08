package com.team.blog.mission.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.post.application.PostAccessPolicy;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 같은 주제로 쓰기·릴레이(034, 강성찬 개인 확장). 미션은 이메일 인증한 회원이 연다(제목 40자, 설명 500자, 기간 1~30일).
 * 참여는 내 공개 글 하나를 진행 중인 미션에 잇는 것 — 한 미션에 한 사람 한 글, 참여 순서가 릴레이 순서다.
 * 참여 글 목록은 공용 목록 조건(공개 글만)을 거친다.
 */
@Service
public class MissionService {

    public record Mission(long id, String title, String description, Instant startsAt, Instant endsAt, String creatorHandle,
                          String creatorNickname, int participants, boolean active) {
    }

    public record Entry(int order, long postId, String title, String url, String handle, String nickname) {
    }

    private final JdbcTemplate jdbc;
    private final AccountGuard accountGuard;
    private final RedisRateLimiter rateLimiter;
    private final MissionProperties properties;
    private final PostAccessPolicy accessPolicy;
    private final Clock clock;

    public MissionService(JdbcTemplate jdbc, AccountGuard accountGuard, RedisRateLimiter rateLimiter,
                          MissionProperties properties, PostAccessPolicy accessPolicy, Clock clock) {
        this.jdbc = jdbc;
        this.accountGuard = accountGuard;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public long create(Optional<CurrentUser> current, String rawTitle, String rawDescription, int days) {
        CurrentUser me = accountGuard.requireWritable(current);
        String title = rawTitle == null ? "" : rawTitle.strip();
        String description = rawDescription == null ? "" : rawDescription.strip();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > 40) {
            throw new PostContentException("MISSION_TITLE_INVALID");
        }
        if (description.codePointCount(0, description.length()) > 500) {
            throw new PostContentException("MISSION_DESCRIPTION_TOO_LONG");
        }
        if (days < 1 || days > properties.maxDays()) {
            throw new PostContentException("MISSION_PERIOD_INVALID");
        }
        RedisRateLimiter.Result r = rateLimiter.tryAcquire(RedisRateLimiter.key("mission:create", String.valueOf(me.memberId())),
                properties.perDay(), Duration.ofDays(1));
        if (!r.allowed()) {
            throw new RateLimitedException(r.retryAfterSeconds());
        }
        Instant now = clock.instant();
        return jdbc.queryForObject("""
                INSERT INTO mission (creator_id, title, description, starts_at, ends_at) VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, me.memberId(), title, description, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofDays(days))));
    }

    /** 진행 중(끝나는 날 가까운 순) 또는 끝난 미션(최근 순). */
    public List<Mission> list(boolean active, int limit) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.query(SELECT + (active ? " WHERE mi.ends_at > ? ORDER BY mi.ends_at, mi.id" : " WHERE mi.ends_at <= ? ORDER BY mi.ends_at DESC, mi.id DESC")
                + " LIMIT " + limit, (rs, n) -> mission(rs, now), now);
    }

    public Mission get(long id) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.query(SELECT + " WHERE mi.id = ?", (rs, n) -> mission(rs, now), id).stream().findFirst()
                .orElseThrow(NotFoundException::new);
    }

    /** 참여 글(참여 순서, 지금 공개인 글만). 번호는 지금 보이는 글 기준으로 다시 매긴다. */
    public List<Entry> entries(long missionId) {
        List<Object[]> rows = jdbc.query("""
                SELECT p.id, p.title, m.handle, m.nickname FROM mission_participant mp
                JOIN post p ON p.id = mp.post_id JOIN member m ON m.id = p.author_id
                WHERE mp.mission_id = ? AND """ + " " + accessPolicy.publicListingCondition("p", "m")
                + " ORDER BY mp.joined_at, mp.post_id", (rs, n) -> new Object[] {rs.getLong("id"), rs.getString("title"),
                rs.getString("handle"), rs.getString("nickname")}, missionId);
        java.util.ArrayList<Entry> out = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            out.add(new Entry(i + 1, (Long) r[0], (String) r[1], "/@" + r[2] + "/posts/" + r[0], (String) r[2], (String) r[3]));
        }
        return out;
    }

    /** 내 공개 글로 진행 중인 미션에 참여(한 미션에 한 사람 한 글, 다시 하면 글을 바꾼다). */
    public void join(Optional<CurrentUser> current, long missionId, long postId) {
        CurrentUser me = accountGuard.requireWritable(current);
        Mission mission = get(missionId);
        if (!mission.active()) {
            throw new PostContentException("MISSION_ENDED");
        }
        Boolean ownPublic = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM post p JOIN member m ON m.id = p.author_id WHERE p.id = ? AND p.author_id = ? AND "
                + accessPolicy.publicListingCondition("p", "m") + ")", Boolean.class, postId, me.memberId());
        if (!Boolean.TRUE.equals(ownPublic)) {
            throw new PostContentException("MISSION_POST_NOT_PUBLIC");
        }
        jdbc.update("""
                INSERT INTO mission_participant (mission_id, member_id, post_id, joined_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (mission_id, member_id) DO UPDATE SET post_id = EXCLUDED.post_id
                """, missionId, me.memberId(), postId, Timestamp.from(clock.instant()));
    }

    public void leave(Optional<CurrentUser> current, long missionId) {
        CurrentUser me = accountGuard.requireLoggedIn(current);
        jdbc.update("DELETE FROM mission_participant WHERE mission_id = ? AND member_id = ?", missionId, me.memberId());
    }

    /** 글이 참여한 미션(배지용). */
    public List<Mission> missionsOfPost(long postId) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.query(SELECT + " WHERE mi.id IN (SELECT mission_id FROM mission_participant WHERE post_id = ?) ORDER BY mi.id",
                (rs, n) -> mission(rs, now), postId);
    }

    public void purgeWithdrawn(long memberId) {
        jdbc.update("DELETE FROM mission_participant WHERE member_id = ?", memberId);
    }

    private static final String SELECT = """
            SELECT mi.id, mi.title, mi.description, mi.starts_at, mi.ends_at, c.handle, c.nickname, c.withdrawn_at,
                   (SELECT count(*) FROM mission_participant mp WHERE mp.mission_id = mi.id) AS participants
            FROM mission mi JOIN member c ON c.id = mi.creator_id""";

    private static Mission mission(java.sql.ResultSet rs, Timestamp now) throws java.sql.SQLException {
        boolean gone = rs.getTimestamp("withdrawn_at") != null;
        return new Mission(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                gone ? null : rs.getString("handle"), gone ? "탈퇴한 사용자" : rs.getString("nickname"),
                rs.getInt("participants"), rs.getTimestamp("ends_at").after(now));
    }
}

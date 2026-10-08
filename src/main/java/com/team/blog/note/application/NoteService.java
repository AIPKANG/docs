package com.team.blog.note.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.friend.application.FriendQuery;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 짧은 기록(033, 강성찬 개인 확장): 글자만(링크·서식 없음), 280자. 공개 범위는 전체 공개·친구 공개(025)·나만 보기.
 * 쓰기는 이메일 인증한 회원만, 한 시간 20개까지.
 */
@Service
public class NoteService {

    public record Note(long id, String content, String visibility, Instant createdAt, boolean mine) {
    }

    private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "FRIENDS", "PRIVATE");

    private final JdbcTemplate jdbc;
    private final AccountGuard accountGuard;
    private final RedisRateLimiter rateLimiter;
    private final NoteProperties properties;
    private final FriendQuery friends;

    public NoteService(JdbcTemplate jdbc, AccountGuard accountGuard, RedisRateLimiter rateLimiter, NoteProperties properties,
                       FriendQuery friends) {
        this.jdbc = jdbc;
        this.accountGuard = accountGuard;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.friends = friends;
    }

    public long write(Optional<CurrentUser> current, String rawContent, String visibility) {
        CurrentUser me = accountGuard.requireWritable(current);
        String content = rawContent == null ? "" : rawContent.strip().replace("\r\n", "\n");
        if (content.isEmpty()) {
            throw new PostContentException("NOTE_EMPTY");
        }
        if (content.codePointCount(0, content.length()) > properties.maxLength()) {
            throw new PostContentException("NOTE_TOO_LONG");
        }
        String v = visibility == null || !VISIBILITIES.contains(visibility) ? "PUBLIC" : visibility;
        RedisRateLimiter.Result r = rateLimiter.tryAcquire(RedisRateLimiter.key("note:write", String.valueOf(me.memberId())),
                properties.perHour(), Duration.ofHours(1));
        if (!r.allowed()) {
            throw new RateLimitedException(r.retryAfterSeconds());
        }
        return jdbc.queryForObject("INSERT INTO short_note (author_id, content, visibility) VALUES (?, ?, ?) RETURNING id",
                Long.class, me.memberId(), content, v);
    }

    public void delete(Optional<CurrentUser> current, long noteId) {
        CurrentUser me = accountGuard.requireLoggedIn(current);
        if (jdbc.update("DELETE FROM short_note WHERE id = ? AND author_id = ?", noteId, me.memberId()) == 0) {
            throw new NotFoundException();
        }
    }

    /** 보는 사람에게 보이는 기록(최신순). {@code beforeId}: 이어 보기. */
    public List<Note> notes(long authorId, Optional<CurrentUser> viewer, Long beforeId, int limit) {
        long me = viewer.map(CurrentUser::memberId).orElse(-1L);
        boolean owner = me == authorId;
        boolean friend = !owner && me > 0 && friends.areFriends(me, authorId);
        String visible = owner ? "TRUE" : friend ? "n.visibility IN ('PUBLIC', 'FRIENDS')" : "n.visibility = 'PUBLIC'";
        return jdbc.query("SELECT n.id, n.content, n.visibility, n.created_at FROM short_note n WHERE n.author_id = ? AND "
                        + visible + (beforeId == null ? "" : " AND n.id < " + beforeId) + " ORDER BY n.id DESC LIMIT " + limit,
                (rs, i) -> new Note(rs.getLong("id"), rs.getString("content"), rs.getString("visibility"),
                        rs.getTimestamp("created_at").toInstant(), owner), authorId);
    }

    public int pageSize() {
        return properties.pageSize();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** 탈퇴 정리. */
    public void purgeWithdrawn(long memberId) {
        jdbc.update("DELETE FROM short_note WHERE author_id = ?", memberId);
    }
}

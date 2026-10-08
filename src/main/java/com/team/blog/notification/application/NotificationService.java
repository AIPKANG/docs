package com.team.blog.notification.application;

import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받는 사람 본인의 알림 다루기(FR-026~FR-031, 42 §10-3). 비회원 401, 인증 전도 본인 것은 됨, 남의 알림은 관리자도 404.
 * 주소에 회원 번호가 없고 로그인 정보로만 찾는다.
 */
@Service
public class NotificationService {

    private final JdbcTemplate jdbc;
    private final AccountGuard accountGuard;
    private final NotificationQuery query;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationService(JdbcTemplate jdbc, AccountGuard accountGuard, NotificationQuery query,
                               NotificationProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.accountGuard = accountGuard;
        this.query = query;
        this.properties = properties;
        this.clock = clock;
    }

    public long unreadCount(Optional<CurrentUser> current) {
        return query.unreadCount(accountGuard.requireLoggedIn(current).memberId());
    }

    public NotificationPage page(Optional<CurrentUser> current, String cursor, Integer size) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        return query.page(user, cursor, size == null ? properties.pageSize() : size);
    }

    /** 하나 읽음. 이미 읽었어도 204. 이동할 주소를 함께 돌려준다(폼 제출용). */
    public Optional<String> read(Optional<CurrentUser> current, long id) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        int found = jdbc.update("UPDATE notification SET read_at = COALESCE(read_at, ?) WHERE id = ? AND receiver_id = ?",
                Timestamp.from(clock.instant()), id, user.memberId());
        if (found == 0) {
            throw new NotFoundException();
        }
        return query.one(user, id).map(NotificationItem::url);
    }

    /** 누른 시각까지의 안 읽은 알림 모두 읽음. */
    public int readAll(Optional<CurrentUser> current) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.update("UPDATE notification SET read_at = ? WHERE receiver_id = ? AND read_at IS NULL AND updated_at <= ?",
                now, user.memberId(), now);
    }

    public void delete(Optional<CurrentUser> current, long id) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        if (jdbc.update("DELETE FROM notification WHERE id = ? AND receiver_id = ?", id, user.memberId()) == 0) {
            throw new NotFoundException();
        }
    }

    /** 끌 수 있는 다섯 종류의 켜짐 여부(행이 없으면 켜짐). */
    public Map<String, Boolean> settings(Optional<CurrentUser> current) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        List<String> muted = jdbc.queryForList("SELECT type FROM notification_mute WHERE member_id = ?", String.class,
                user.memberId());
        Map<String, Boolean> result = new LinkedHashMap<>();
        NotificationType.MUTABLE.forEach(t -> result.put(t.name(), !muted.contains(t.name())));
        return result;
    }

    /** 보낸 종류만 바꾼다. 모르는 종류(운영 알림 포함)는 무시. */
    @Transactional
    public Map<String, Boolean> updateSettings(Optional<CurrentUser> current, Map<String, Boolean> changes) {
        CurrentUser user = accountGuard.requireLoggedIn(current);
        for (NotificationType type : NotificationType.MUTABLE) {
            Boolean on = changes == null ? null : changes.get(type.name());
            if (on == null) {
                continue;
            }
            if (on) {
                jdbc.update("DELETE FROM notification_mute WHERE member_id = ? AND type = ?", user.memberId(), type.name());
            } else {
                jdbc.update("""
                        INSERT INTO notification_mute (member_id, type, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                        """, user.memberId(), type.name(), Timestamp.from(clock.instant()));
            }
        }
        return settings(current);
    }
}

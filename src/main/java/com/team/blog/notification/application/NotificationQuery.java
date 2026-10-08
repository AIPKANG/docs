package com.team.blog.notification.application;

import com.team.blog.account.application.AuthorDisplay;
import com.team.blog.post.application.CardDates;
import com.team.blog.post.application.FeedCursor;
import com.team.blog.post.application.PostAccessPolicy;
import com.team.blog.post.application.visibility.PostFacts;
import com.team.blog.post.domain.PostStatus;
import com.team.blog.shared.security.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 알림 목록·안 읽은 수(25 §5). 목록은 알림 + 행동한 사람 + 글·작성자 + 댓글을 한 번에 읽고(FR-030), 읽기 판정은 그 결과로
 * {@link PostAccessPolicy}에 맡긴다. 정렬 {@code updated_at DESC, id DESC}, 커서 {@code (updated_at, id)}.
 */
@Service
public class NotificationQuery {

    public static final String UNAVAILABLE = "볼 수 없는 글이에요";

    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationQuery(JdbcTemplate jdbc, PostAccessPolicy accessPolicy, NotificationProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    public long unreadCount(long memberId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM notification WHERE receiver_id = ? AND read_at IS NULL", Long.class,
                memberId);
        return n == null ? 0 : n;
    }

    public NotificationPage page(CurrentUser user, String cursor, int size) {
        Optional<FeedCursor> after = FeedCursor.decode(cursor);
        int limit = Math.max(1, Math.min(size, properties.pageSize()));
        List<Object> args = new ArrayList<>();
        args.add(user.memberId());
        String where = "";
        if (after.isPresent()) {
            where = " AND (n.updated_at, n.id) < (?, ?)";
            args.add(Timestamp.from(after.get().firstPublicAt()));
            args.add(after.get().id());
        }
        args.add(limit + 1);
        List<NotificationItem> rows = select(user, where, args);
        String next = null;
        if (rows.size() > limit) {
            rows = new ArrayList<>(rows.subList(0, limit));
            NotificationItem last = rows.get(limit - 1);
            next = new FeedCursor(last.updatedAt(), last.id()).encode();
        }
        return new NotificationPage(rows, next);
    }

    /** 본인 알림 하나(읽음 처리 뒤 이동 주소). */
    public Optional<NotificationItem> one(CurrentUser user, long id) {
        List<Object> args = new ArrayList<>(List.of(user.memberId(), id, 1));
        return select(user, " AND n.id = ?", args).stream().findFirst();
    }

    private List<NotificationItem> select(CurrentUser user, String where, List<Object> args) {
        Instant now = clock.instant();
        return jdbc.query("""
                SELECT n.id, n.type, n.read_at, n.updated_at, n.actor_count, n.result, n.post_id, n.comment_id,
                       a.handle AS a_handle, a.nickname AS a_nickname, a.profile_image_url AS a_picture, a.withdrawn_at AS a_withdrawn,
                       n.last_actor_id,
                       p.title, p.status, p.visibility, p.author_id, p.deleted_at AS p_deleted, p.hidden_at AS p_hidden,
                       pm.handle AS p_handle, pm.withdrawn_at AS p_author_withdrawn,
                       c.content, c.deleted_at AS c_deleted, c.hidden_at AS c_hidden
                FROM notification n
                LEFT JOIN member a ON a.id = n.last_actor_id
                LEFT JOIN post p ON p.id = n.post_id
                LEFT JOIN member pm ON pm.id = p.author_id
                LEFT JOIN comment c ON c.id = n.comment_id
                WHERE n.receiver_id = ?""" + where + """

                ORDER BY n.updated_at DESC, n.id DESC
                LIMIT ?
                """, (rs, i) -> item(rs, user, now), args.toArray());
    }

    private NotificationItem item(ResultSet rs, CurrentUser user, Instant now) throws SQLException {
        NotificationType type = NotificationType.valueOf(rs.getString("type"));
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        boolean read = rs.getTimestamp("read_at") != null;
        NotificationItem.Actor actor = null;
        String actorName = AuthorDisplay.WITHDRAWN_LABEL;
        if (rs.getObject("last_actor_id") != null) {
            Timestamp w = rs.getTimestamp("a_withdrawn");
            AuthorDisplay d = AuthorDisplay.of(rs.getString("a_handle"), rs.getString("a_nickname"),
                    w == null ? null : w.toInstant(), rs.getString("a_picture"));
            actor = d.withdrawn() ? new NotificationItem.Actor(AuthorDisplay.WITHDRAWN_LABEL, null, null, true)
                    : new NotificationItem.Actor(d.nickname(), d.handle(), d.profileImageUrl(), false);
            actorName = d.shortLabel();
        }
        int count = rs.getInt("actor_count");
        Integer others = (type == NotificationType.LIKE || type == NotificationType.FOLLOW) ? Math.max(0, count - 1) : null;
        String title = null;
        String postUrl = null;
        boolean readable = false;
        boolean ownHidden = false;
        long postId = rs.getLong("post_id");
        if (!rs.wasNull() && rs.getString("status") != null) {
            boolean authorWithdrawn = rs.getTimestamp("p_author_withdrawn") != null;
            boolean deleted = rs.getTimestamp("p_deleted") != null;
            boolean hidden = rs.getTimestamp("p_hidden") != null;
            long authorId = rs.getLong("author_id");
            PostFacts facts = new PostFacts(postId, authorId, PostStatus.valueOf(rs.getString("status")),
                    rs.getString("visibility"), authorWithdrawn, false);
            boolean canRead = !deleted && PostStatus.PUBLISHED.name().equals(rs.getString("status"))
                    && accessPolicy.canRead(Optional.of(user), facts);
            readable = canRead && !hidden;
            ownHidden = canRead && hidden && authorId == user.memberId();
            title = rs.getString("title");
            postUrl = "/@" + rs.getString("p_handle") + "/posts/" + postId;
        }
        NotificationItem.PostRef post = null;
        String preview = null;
        String message;
        String url = null;
        Long commentId = rs.getObject("comment_id") == null ? null : rs.getLong("comment_id");
        String commentUrl = commentId == null || postUrl == null ? postUrl : postUrl + "?comment=" + commentId + "#comment-" + commentId;
        switch (type) {
            case COMMENT, REPLY -> {
                boolean commentOk = rs.getString("content") != null && rs.getTimestamp("c_deleted") == null
                        && rs.getTimestamp("c_hidden") == null;
                if (readable && commentOk) {
                    post = new NotificationItem.PostRef(title, postUrl, null);
                    preview = preview(rs.getString("content"));
                    url = commentUrl;
                    message = type == NotificationType.COMMENT
                            ? actorName + "님이 「" + title + "」에 댓글을 남겼어요: " + preview
                            : actorName + "님이 회원님의 댓글에 답글을 남겼어요: " + preview;
                } else {
                    post = new NotificationItem.PostRef(null, null, true);
                    message = UNAVAILABLE;
                }
            }
            case LIKE -> {
                if (readable) {
                    post = new NotificationItem.PostRef(title, postUrl, null);
                    url = postUrl;
                    message = who(actorName, others) + " 「" + title + "」을(를) 좋아해요";
                } else {
                    post = new NotificationItem.PostRef(null, null, true);
                    message = UNAVAILABLE;
                }
            }
            case NEW_POST -> {
                if (readable) {
                    post = new NotificationItem.PostRef(title, postUrl, null);
                    url = postUrl;
                    message = actorName + "님이 새 글을 올렸어요: 「" + title + "」";
                } else {
                    post = new NotificationItem.PostRef(null, null, true);
                    message = UNAVAILABLE;
                }
            }
            case FOLLOW -> {
                url = "/me/followers";
                message = who(actorName, others) + " 회원님을 팔로우해요";
            }
            case REPORT_RESOLVED -> message = "ACTION_TAKEN".equals(rs.getString("result"))
                    ? "신고하신 내용을 검토해 조치했어요. 알려 주셔서 고마워요"
                    : "신고하신 내용을 검토했지만 운영 정책 위반은 아니었어요";
            case CONTENT_HIDDEN -> {
                if (commentId != null) {
                    message = "회원님의 댓글이 운영 정책에 따라 숨겨졌어요";
                    url = (readable || ownHidden) ? commentUrl : null;
                } else if (readable || ownHidden) {
                    post = new NotificationItem.PostRef(title, postUrl, null);
                    message = "회원님의 글「" + title + "」이(가) 운영 정책에 따라 숨겨졌어요";
                    url = postUrl;
                } else {
                    message = "회원님의 글이 운영 정책에 따라 숨겨졌어요";
                }
            }
            default -> message = "";
        }
        if (type == NotificationType.REPORT_RESOLVED || type == NotificationType.CONTENT_HIDDEN) {
            actor = null;
        }
        return new NotificationItem(rs.getLong("id"), type.name(), read, updatedAt, CardDates.label(updatedAt, now), actor,
                others, post, preview, rs.getString("result"), message, url);
    }

    private static String who(String actorName, Integer others) {
        return others == null || others == 0 ? actorName + "님이" : actorName + "님 외 " + others + "명이";
    }

    private String preview(String content) {
        String flat = content.replaceAll("\\s+", " ").strip();
        int max = properties.previewLength();
        if (flat.codePointCount(0, flat.length()) <= max) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, max)) + "…";
    }
}

package com.team.blog.post.application;

import com.team.blog.media.application.PostImageService;
import com.team.blog.post.domain.PostContentRules;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 글 삭제·복구·영구 삭제(011 R-2, 13 §2). 작성자만(남의 글 404), 인증 전 회원도 자기 글은 지울 수 있다(42 §5-2).
 * 같은 글의 요청은 행 잠금으로 하나씩 처리한다. 버퍼 삭제는 커밋 후.
 */
@Service
public class PostTrashService {

    private static final Logger log = LoggerFactory.getLogger(PostTrashService.class);

    public record TrashResult(boolean trashed, boolean purged, Instant purgeAt) {
    }

    private final AccountGuard accountGuard;
    private final JdbcTemplate jdbc;
    private final AutosaveFlusher flusher;
    private final AutosaveBuffer buffer;
    private final BufferCircuit circuit;
    private final PostImageService postImageService;
    private final TransactionTemplate transactionTemplate;
    private final PostProperties properties;
    private final Clock clock;

    public PostTrashService(AccountGuard accountGuard, JdbcTemplate jdbc, AutosaveFlusher flusher, AutosaveBuffer buffer,
                            BufferCircuit circuit, PostImageService postImageService,
                            TransactionTemplate transactionTemplate, PostProperties properties, Clock clock) {
        this.accountGuard = accountGuard;
        this.jdbc = jdbc;
        this.flusher = flusher;
        this.buffer = buffer;
        this.circuit = circuit;
        this.postImageService = postImageService;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    private record Locked(String status, String title, String contentMd, Instant deletedAt) {
    }

    private Optional<Locked> lock(long postId, long authorId) {
        return jdbc.query("""
                SELECT status, title, content_md, deleted_at FROM post WHERE id = ? AND author_id = ? FOR UPDATE
                """, (rs, n) -> new Locked(rs.getString("status"), rs.getString("title"), rs.getString("content_md"),
                        rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()),
                postId, authorId).stream().findFirst();
    }

    /** 휴지통으로(빈 임시글은 바로 완전 삭제). 이미 휴지통이면 그대로 성공. */
    public TrashResult trash(Optional<CurrentUser> currentUser, long postId) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        // FR-023: 옮기기 직전까지의 자동 저장분을 먼저 글에 반영(소유 확인 전이라도 버퍼 반영은 무해 — 남의 글이면 아래에서 404)
        if (circuit.allowsRedis()) {
            try {
                flusher.flushOne(postId);
            } catch (RuntimeException e) {
                log.warn("autosave flush before trash failed for post {}: {}", postId, e.getMessage());
            }
        }
        Instant now = clock.instant();
        TrashResult result = transactionTemplate.execute(status -> {
            Locked post = lock(postId, user.memberId()).orElseThrow(NotFoundException::new);
            if (post.deletedAt() != null) {
                return new TrashResult(true, false, post.deletedAt().plus(properties.trash().retention()));
            }
            if ("DRAFT".equals(post.status()) && PostContentRules.isBlank(post.title(), post.contentMd())) {
                purgeLocked(postId, now);
                return new TrashResult(false, true, null);
            }
            jdbc.update("UPDATE post SET deleted_at = ? WHERE id = ?", Timestamp.from(now), postId);
            return new TrashResult(true, false, now.plus(properties.trash().retention()));
        });
        evictBuffer(postId);
        return result;
    }

    /** 복구: 휴지통 글만(아니면 404). 상태·공개 범위·처음 공개 시각 그대로 → 목록의 원래 위치. */
    public void restore(Optional<CurrentUser> currentUser, long postId) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        transactionTemplate.executeWithoutResult(status -> {
            Locked post = lock(postId, user.memberId()).orElseThrow(NotFoundException::new);
            if (post.deletedAt() == null) {
                throw new NotFoundException();
            }
            jdbc.update("UPDATE post SET deleted_at = NULL WHERE id = ?", postId);
        });
    }

    /** 영구 삭제: 휴지통 글만(아니면 404). */
    public void purge(Optional<CurrentUser> currentUser, long postId) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            Locked post = lock(postId, user.memberId()).orElseThrow(NotFoundException::new);
            if (post.deletedAt() == null) {
                throw new NotFoundException();
            }
            purgeLocked(postId, now);
        });
        evictBuffer(postId);
    }

    /** 30일 자동 정리(011 R-3): 한 묶음. 지운 글 수. */
    public int purgeExpired() {
        Instant now = clock.instant();
        Instant before = now.minus(properties.trash().retention());
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM post WHERE deleted_at IS NOT NULL AND deleted_at < ? ORDER BY deleted_at, id LIMIT ?
                """, Long.class, Timestamp.from(before), properties.trash().purgeBatchSize());
        int purged = 0;
        for (Long id : ids) {
            try {
                Boolean done = transactionTemplate.execute(status -> {
                    List<Long> locked = jdbc.queryForList(
                            "SELECT id FROM post WHERE id = ? AND deleted_at IS NOT NULL AND deleted_at < ? FOR UPDATE",
                            Long.class, id, Timestamp.from(before));
                    if (locked.isEmpty()) {
                        return false;
                    }
                    purgeLocked(id, now);
                    return true;
                });
                if (Boolean.TRUE.equals(done)) {
                    purged++;
                    evictBuffer(id);
                }
            } catch (RuntimeException e) {
                log.warn("trash purge failed for post {}: {}", id, e.getMessage());
            }
        }
        return purged;
    }

    /**
     * 완전 삭제(FR-029~FR-031): 이 글에서만 쓰던 사진은 끊긴 시각을 기록하고(7일 뒤 사진 정리), 글을 지운다 — 댓글·좋아요·태그 연결·
     * 사진 연결·작업본은 FK CASCADE로 함께 지워지고 태그 자체는 남는다. 글 번호는 IDENTITY라 재사용되지 않는다.
     */
    private void purgeLocked(long postId, Instant now) {
        postImageService.detachAll(postId, now);
        jdbc.update("DELETE FROM post WHERE id = ?", postId);
    }

    private void evictBuffer(long postId) {
        if (!circuit.allowsRedis()) {
            return;
        }
        try {
            buffer.evict(postId);
        } catch (DataAccessException e) {
            circuit.recordFailure();
        }
    }
}

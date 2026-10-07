package com.team.blog.post.infra;

import com.team.blog.post.application.MyPostRow;
import com.team.blog.post.application.PostEditRow;
import com.team.blog.post.domain.PostStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 편집 저장소(JDBC). 영구 반영은 "더 오래된 버전으로 덮어쓰지 않는" 조건부 SQL이다(04 §2-4, research R-4).
 * 임시글은 {@code post}에, 발행 글은 {@code post_draft} 작업본에 쓴다. {@code content_html}은 만들지 않는다(FR-007).
 */
@Repository
public class PostEditStore {

    private static final String SELECT_ROW = """
            SELECT p.id, p.author_id, p.status, p.visibility, p.title, p.content_md, p.edit_version, p.updated_at,
                   d.edit_version AS d_version, d.title AS d_title, d.content_md AS d_content, d.updated_at AS d_updated_at
            FROM post p LEFT JOIN post_draft d ON d.post_id = p.id
            WHERE p.id = ? AND p.deleted_at IS NULL
            """;

    private static final RowMapper<PostEditRow> ROW = PostEditStore::mapRow;

    private final JdbcTemplate jdbc;

    public PostEditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 휴지통 밖 글. 없거나 휴지통이면 빈 값. */
    public Optional<PostEditRow> findActive(long postId) {
        return jdbc.query(SELECT_ROW, ROW, postId).stream().findFirst();
    }

    /** 새 임시글({@code edit_version} 0). */
    public long insertDraft(long authorId, String title, String contentMd, String visibility, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO post (author_id, title, content_md, status, visibility, edit_version, created_at, updated_at)
                VALUES (?, ?, ?, 'DRAFT', ?, 0, ?, ?) RETURNING id
                """, Long.class, authorId, title, contentMd, visibility, ts(now), ts(now));
        return id;
    }

    /** 임시글 반영: 현재 DB 버전보다 새 버전일 때만. 바뀐 행 수. */
    public int flushDraft(long postId, String title, String contentMd, long version, Instant now) {
        return jdbc.update("""
                UPDATE post SET title = ?, content_md = ?, edit_version = ?, updated_at = ?
                WHERE id = ? AND status = 'DRAFT' AND deleted_at IS NULL AND edit_version < ?
                """, title, contentMd, version, ts(now), postId, version);
    }

    /**
     * 발행 글 작업본 반영: 발행본 버전보다 새 버전이고(변경 취소·다시 발행 뒤 옛 버퍼가 되살리지 못하게) 작업본보다도 새 버전일 때만.
     */
    public int flushWorkingCopy(long postId, String title, String contentMd, long version, Instant now) {
        return jdbc.update("""
                INSERT INTO post_draft (post_id, title, content_md, edit_version, created_at, updated_at)
                SELECT p.id, ?, ?, ?, ?, ? FROM post p
                WHERE p.id = ? AND p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND p.edit_version < ?
                ON CONFLICT (post_id) DO UPDATE
                SET title = EXCLUDED.title, content_md = EXCLUDED.content_md,
                    edit_version = EXCLUDED.edit_version, updated_at = EXCLUDED.updated_at
                WHERE post_draft.edit_version < EXCLUDED.edit_version
                """, title, contentMd, version, ts(now), ts(now), postId, version);
    }

    /** DB 경로 저장 결과. {@code row}는 판정할 때 잠근 상태. */
    public record DirectSave(boolean accepted, long version, PostEditRow row) {
    }

    /**
     * 버퍼 없이 DB에 바로 저장(Redis 장애, research R-6): 행을 잠그고 DB 현재 버전과 {@code baseVersion}이 같을 때만 +1.
     * 소유 판정은 호출자가 먼저 한다. 글이 그사이 사라지면 빈 값.
     */
    @Transactional
    public Optional<DirectSave> saveDirect(long postId, long baseVersion, String title, String contentMd, Instant now) {
        List<Long> locked = jdbc.queryForList(
                "SELECT id FROM post WHERE id = ? AND deleted_at IS NULL FOR UPDATE", Long.class, postId);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        jdbc.queryForList("SELECT post_id FROM post_draft WHERE post_id = ? FOR UPDATE", Long.class, postId);
        PostEditRow row = findActive(postId).orElseThrow();
        long current = row.dbVersion();
        if (current != baseVersion) {
            return Optional.of(new DirectSave(false, current, row));
        }
        long next = current + 1;
        int updated = row.status() == PostStatus.DRAFT
                ? flushDraft(postId, title, contentMd, next, now)
                : flushWorkingCopy(postId, title, contentMd, next, now);
        if (updated != 1) {
            throw new IllegalStateException("direct save did not apply for post " + postId);
        }
        return Optional.of(new DirectSave(true, next, row));
    }

    /**
     * 변경 취소(research R-8): 작업본을 지우고 발행본의 {@code edit_version}을 {@code currentVersion}까지 올린다(버전은 줄지 않음).
     * 발행 글이 아니면 {@code false}.
     */
    @Transactional
    public boolean discardWorkingCopy(long postId, long currentVersion) {
        List<String> status = jdbc.queryForList(
                "SELECT status FROM post WHERE id = ? AND deleted_at IS NULL FOR UPDATE", String.class, postId);
        if (status.isEmpty() || !PostStatus.PUBLISHED.name().equals(status.get(0))) {
            return false;
        }
        jdbc.update("DELETE FROM post_draft WHERE post_id = ?", postId);
        jdbc.update("UPDATE post SET edit_version = GREATEST(edit_version, ?) WHERE id = ?", currentVersion, postId);
        return true;
    }

    /** 발행 트랜잭션 ③(05 §7): 글 행(과 작업본 행)을 잠그고 읽는다. 휴지통·없음이면 빈 값. 트랜잭션 안에서 불러야 한다. */
    public Optional<PostEditRow> lockForPublish(long postId) {
        List<Long> locked = jdbc.queryForList(
                "SELECT id FROM post WHERE id = ? AND deleted_at IS NULL FOR UPDATE", Long.class, postId);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        jdbc.queryForList("SELECT post_id FROM post_draft WHERE post_id = ? FOR UPDATE", Long.class, postId);
        return findActive(postId);
    }

    /** 발행 반영 결과 시각. */
    public record PublishedTimes(Instant publishedAt, Instant firstPublicAt, Instant editedAt) {
    }

    /**
     * 발행 반영 ⑦(05 §7): 상태 발행, {@code published_at}은 처음 한 번, {@code first_public_at}은 처음 "발행 + 공개"일 때 한 번,
     * {@code edited_at}은 다시 발행일 때만(SET 식의 {@code status}는 바꾸기 전 값). 조회수·좋아요·댓글은 건드리지 않는다.
     */
    public PublishedTimes applyPublish(long postId, String title, String contentMd, String contentHtml, String excerpt,
                                       String thumbnailUrl, String visibility, int renderVersion, long version, Instant now) {
        OffsetDateTime t = ts(now);
        return jdbc.queryForObject("""
                UPDATE post SET title = ?, content_md = ?, content_html = ?, excerpt = ?, thumbnail_url = ?, visibility = ?,
                       render_version = ?,
                       published_at = COALESCE(published_at, ?),
                       first_public_at = CASE WHEN first_public_at IS NULL AND ? = 'PUBLIC' THEN ? ELSE first_public_at END,
                       edited_at = CASE WHEN status = 'PUBLISHED' THEN ? ELSE edited_at END,
                       status = 'PUBLISHED', edit_version = ?, updated_at = ?
                WHERE id = ?
                RETURNING published_at, first_public_at, edited_at
                """, (rs, n) -> new PublishedTimes(instant(rs, "published_at"), instant(rs, "first_public_at"),
                        instant(rs, "edited_at")),
                title, contentMd, contentHtml, excerpt, thumbnailUrl, visibility, renderVersion, t, visibility, t, t,
                version, t, postId);
    }

    public void deleteWorkingCopy(long postId) {
        jdbc.update("DELETE FROM post_draft WHERE post_id = ?", postId);
    }

    /** 내 글(휴지통 밖), 최근 수정 순. {@code editing}은 DB 작업본 기준 — 버퍼 기준 보정은 호출자가 한다. */
    public List<MyPostRow> listByAuthor(long authorId, int limit) {
        return jdbc.query("""
                SELECT p.id, p.title, p.status, p.visibility, p.updated_at, p.edit_version, d.edit_version AS d_version
                FROM post p LEFT JOIN post_draft d ON d.post_id = p.id
                WHERE p.author_id = ? AND p.deleted_at IS NULL
                ORDER BY p.updated_at DESC, p.id DESC LIMIT ?
                """, (rs, n) -> {
            PostStatus st = PostStatus.valueOf(rs.getString("status"));
            long dVersion = rs.getLong("d_version");
            boolean editing = st == PostStatus.PUBLISHED && !rs.wasNull() && dVersion > rs.getLong("edit_version");
            return new MyPostRow(rs.getLong("id"), rs.getString("title"), st, editing, instant(rs, "updated_at"),
                    rs.getString("visibility"));
        }, authorId, limit);
    }

    /** 빈 임시글 정리 후보(04 §2-5): 제목·본문이 공백·탭·줄바꿈뿐, 만든 지·마지막 수정 후 모두 {@code cutoff} 이전. */
    public List<Long> emptyDraftCandidates(Instant cutoff, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM post
                WHERE status = 'DRAFT' AND deleted_at IS NULL AND btrim(title, E' \\t\\r\\n') = '' AND btrim(content_md, E' \\t\\r\\n') = ''
                  AND created_at < ? AND updated_at < ?
                ORDER BY id LIMIT ?
                """, Long.class, ts(cutoff), ts(cutoff), limit);
    }

    /** 같은 조건을 다시 걸어 지운다(그사이 고쳐졌으면 0). */
    public int deleteEmptyDraft(long postId, Instant cutoff) {
        return jdbc.update("""
                DELETE FROM post
                WHERE id = ? AND status = 'DRAFT' AND deleted_at IS NULL AND btrim(title, E' \\t\\r\\n') = '' AND btrim(content_md, E' \\t\\r\\n') = ''
                  AND created_at < ? AND updated_at < ?
                """, postId, ts(cutoff), ts(cutoff));
    }

    private static PostEditRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        long draftVersion = rs.getLong("d_version");
        Long dv = rs.wasNull() ? null : draftVersion;
        return new PostEditRow(rs.getLong("id"), rs.getLong("author_id"), PostStatus.valueOf(rs.getString("status")),
                rs.getString("title"), rs.getString("content_md"), rs.getLong("edit_version"), instant(rs, "updated_at"),
                dv, rs.getString("d_title"), rs.getString("d_content"), instant(rs, "d_updated_at"), rs.getString("visibility"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime ts(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

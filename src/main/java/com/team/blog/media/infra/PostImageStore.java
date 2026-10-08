package com.team.blog.media.infra;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 글-사진 연결({@code post_image}, media 소유) 저장. */
@Repository
public class PostImageStore {

    private final JdbcTemplate jdbc;

    public PostImageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Array textArray(List<String> values) {
        return jdbc.execute((ConnectionCallback<Array>) c -> c.createArrayOf("text", values.toArray()));
    }

    private Array bigintArray(List<Long> values) {
        return jdbc.execute((ConnectionCallback<Array>) c -> c.createArrayOf("bigint", values.toArray()));
    }

    /** 저장 키 → (사진 번호, 썸네일 키) 중 이 회원이 올린 글 용도 사진만. */
    public record OwnedImage(long id, String storageKey, String thumbStorageKey) {
    }

    public List<OwnedImage> ownedPostImages(long uploaderId, List<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT id, storage_key, thumb_storage_key FROM image
                WHERE uploader_id = ? AND purpose = 'POST' AND storage_key = ANY(?)
                """, (rs, n) -> new OwnedImage(rs.getLong(1), rs.getString(2), rs.getString(3)), uploaderId, textArray(keys));
    }

    /** 저장 키 → 썸네일 키(있으면). 글 용도 사진만. */
    public Map<String, String> thumbnails(List<String> keys) {
        Map<String, String> result = new HashMap<>();
        if (keys.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT storage_key, thumb_storage_key FROM image WHERE purpose = 'POST' AND storage_key = ANY(?)"
                + " AND thumb_storage_key IS NOT NULL", rs -> {
                    result.put(rs.getString(1), rs.getString(2));
                }, textArray(keys));
        return result;
    }

    public List<Long> linkedImageIds(long postId) {
        return jdbc.queryForList("SELECT image_id FROM post_image WHERE post_id = ?", Long.class, postId);
    }

    public void link(long postId, List<Long> imageIds) {
        for (Long id : imageIds) {
            jdbc.update("INSERT INTO post_image (post_id, image_id) VALUES (?, ?) ON CONFLICT DO NOTHING", postId, id);
        }
        if (!imageIds.isEmpty()) {
            jdbc.update("UPDATE image SET status = 'ATTACHED', detached_at = NULL WHERE id = ANY(?)", bigintArray(imageIds));
        }
    }

    /** 연결을 끊고, 다른 글에도 연결돼 있지 않은 사진은 끊긴 시각을 기록한다(7일 뒤 정리). */
    public void unlink(long postId, List<Long> imageIds, Instant now) {
        if (imageIds.isEmpty()) {
            return;
        }
        Array ids = bigintArray(imageIds);
        jdbc.update("DELETE FROM post_image WHERE post_id = ? AND image_id = ANY(?)", postId, ids);
        jdbc.update("""
                UPDATE image SET detached_at = ? WHERE id = ANY(?) AND detached_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM post_image pi WHERE pi.image_id = image.id)
                """, Timestamp.from(now), ids);
    }
}

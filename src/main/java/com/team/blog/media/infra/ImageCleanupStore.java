package com.team.blog.media.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 정리 작업의 조건부 삭제(003 research R-9). 조건을 DELETE 문 안에서 다시 확인하므로, 그사이 [저장]으로 연결된 사진
 * (ATTACHED·detached_at NULL)은 지워지지 않는다. 지운 행의 저장 키(원본·썸네일)를 돌려준다.
 */
@Component
public class ImageCleanupStore {

    private final JdbcTemplate jdbc;

    public ImageCleanupStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return 지웠으면 저장 키 목록, 조건이 더는 맞지 않으면 빈 목록 */
    public List<String> deleteIfStillExpired(long id, Instant tempBefore, Instant detachedBefore) {
        List<String> keys = new ArrayList<>();
        jdbc.query("""
                DELETE FROM image
                WHERE id = ? AND ((status = 'TEMP' AND created_at < ?) OR (detached_at IS NOT NULL AND detached_at < ?))
                RETURNING storage_key, thumb_storage_key
                """, rs -> {
                    keys.add(rs.getString(1));
                    String thumb = rs.getString(2);
                    if (thumb != null) {
                        keys.add(thumb);
                    }
                }, id, Timestamp.from(tempBefore), Timestamp.from(detachedBefore));
        return keys;
    }
}

package com.team.blog.post.application;

import com.team.blog.post.markdown.ContentRenderer;
import com.team.blog.post.markdown.MarkdownProperties;
import com.team.blog.post.markdown.RenderedContent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 다시 렌더링(007 FR-021, 12 §7-7): {@code render_version}이 현재보다 낮은 발행 글을 {@code batch-size}개씩 다시 렌더링해
 * {@code content_html}·{@code excerpt}·{@code render_version}만 바꾼다. "수정됨" 시각·편집 버전·마지막 수정 시각은 그대로다.
 * 한 글의 실패는 건너뛰고 다음 실행에서 다시 시도한다.
 */
@Service
public class RerenderService {

    private static final Logger log = LoggerFactory.getLogger(RerenderService.class);

    private final JdbcTemplate jdbc;
    private final ContentRenderer renderer;
    private final MarkdownProperties properties;

    public RerenderService(JdbcTemplate jdbc, ContentRenderer renderer, MarkdownProperties properties) {
        this.jdbc = jdbc;
        this.renderer = renderer;
        this.properties = properties;
    }

    /** 한 묶음 처리. 다시 렌더링한 글 수. */
    public int runBatch() {
        record Row(long id, String contentMd) {
        }
        List<Row> rows = jdbc.query("""
                SELECT id, content_md FROM post
                WHERE status = 'PUBLISHED' AND render_version < ?
                ORDER BY id LIMIT ?
                """, (rs, n) -> new Row(rs.getLong("id"), rs.getString("content_md")),
                ContentRenderer.RENDER_VERSION, properties.rerender().batchSize());
        int done = 0;
        for (Row row : rows) {
            try {
                RenderedContent rendered = renderer.render(row.contentMd());
                done += jdbc.update("""
                        UPDATE post SET content_html = ?, excerpt = ?, render_version = ?
                        WHERE id = ? AND render_version < ?
                        """, rendered.html(), rendered.excerpt().isEmpty() ? null : rendered.excerpt(),
                        rendered.renderVersion(), row.id(), rendered.renderVersion());
            } catch (RuntimeException e) {
                log.warn("rerender failed for post {}: {}", row.id(), e.getMessage());
            }
        }
        return done;
    }

    /** 남은 글이 없을 때까지(한 묶음이 모두 실패하면 멈춤). */
    public int runAll() {
        int total = 0;
        int done;
        do {
            done = runBatch();
            total += done;
        } while (done > 0);
        return total;
    }
}

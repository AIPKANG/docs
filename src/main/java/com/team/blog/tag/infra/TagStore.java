package com.team.blog.tag.infra;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 태그 저장(22 §4): 없는 이름은 만들고, 글의 태그 연결을 순서대로 바꾼다. */
@Repository
public class TagStore {

    private final JdbcTemplate jdbc;

    public TagStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long ensure(String name) {
        jdbc.update("INSERT INTO tag (name) VALUES (?) ON CONFLICT (name) DO NOTHING", name);
        return jdbc.queryForObject("SELECT id FROM tag WHERE name = ?", Long.class, name);
    }

    public void replacePostTags(long postId, List<Long> tagIds) {
        jdbc.update("DELETE FROM post_tag WHERE post_id = ?", postId);
        for (int i = 0; i < tagIds.size(); i++) {
            jdbc.update("INSERT INTO post_tag (post_id, tag_id, position) VALUES (?, ?, ?)", postId, tagIds.get(i), i);
        }
    }

    public List<String> namesOf(long postId) {
        return jdbc.queryForList("""
                SELECT t.name FROM post_tag pt JOIN tag t ON t.id = pt.tag_id WHERE pt.post_id = ? ORDER BY pt.position
                """, String.class, postId);
    }
}

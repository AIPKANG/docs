package com.team.blog.post.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 글의 반응 수(댓글·좋아요) 증감 — post 테이블은 post 모듈만 쓴다(헌법 I). 호출자의 트랜잭션에 참여한다. */
@Service
public class PostCounters {

    private final JdbcTemplate jdbc;

    public PostCounters(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void adjustComments(long postId, int delta) {
        if (delta != 0) {
            jdbc.update("UPDATE post SET comment_count = GREATEST(0, comment_count + ?) WHERE id = ?", delta, postId);
        }
    }

    public int likeCount(long postId) {
        Integer n = jdbc.queryForObject("SELECT like_count FROM post WHERE id = ?", Integer.class, postId);
        return n == null ? 0 : n;
    }

    /** 좋아요 수를 실제 기록 수와 맞춘다(015 FR-010). 고친 글 수. */
    public int reconcileLikes() {
        return jdbc.update("""
                UPDATE post p SET like_count = c.n
                FROM (SELECT p2.id, count(l.post_id) AS n FROM post p2 LEFT JOIN post_like l ON l.post_id = p2.id GROUP BY p2.id) c
                WHERE c.id = p.id AND p.like_count <> c.n
                """);
    }

    public void adjustLikes(long postId, int delta) {
        if (delta != 0) {
            jdbc.update("UPDATE post SET like_count = GREATEST(0, like_count + ?) WHERE id = ?", delta, postId);
        }
    }
}

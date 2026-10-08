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

    public void adjustLikes(long postId, int delta) {
        if (delta != 0) {
            jdbc.update("UPDATE post SET like_count = GREATEST(0, like_count + ?) WHERE id = ?", delta, postId);
        }
    }
}

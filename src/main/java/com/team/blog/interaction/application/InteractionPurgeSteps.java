package com.team.blog.interaction.application;

import com.team.blog.account.application.WithdrawalPurgeStep;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 023 익명 처리 단계: 20 남의 글에 쓴 댓글(답글이 달린 것은 자리만, 나머지는 삭제, 댓글 수 다시 세기), 30 내가 누른 좋아요(수 다시 세기),
 * 60 팔로우 양방향. 수는 실제 남은 행으로 다시 센다(FR-030).
 */
public final class InteractionPurgeSteps {

    private InteractionPurgeSteps() {
    }

    @Component
    public static class Comments implements WithdrawalPurgeStep {

        private final JdbcTemplate jdbc;
        private final Clock clock;

        public Comments(JdbcTemplate jdbc, Clock clock) {
            this.jdbc = jdbc;
            this.clock = clock;
        }

        @Override
        public int order() {
            return 20;
        }

        @Override
        public void purge(long memberId) {
            Timestamp now = Timestamp.from(clock.instant());
            List<Long> posts = jdbc.queryForList("SELECT DISTINCT post_id FROM comment WHERE author_id = ?", Long.class, memberId);
            // 답글(잎)부터 지운다
            jdbc.update("DELETE FROM comment WHERE author_id = ? AND parent_id IS NOT NULL", memberId);
            // 최상위: 답글이 남아 있으면 자리만, 아니면 삭제
            jdbc.update("""
                    UPDATE comment c SET content = '', deleted_at = COALESCE(c.deleted_at, ?)
                    WHERE c.author_id = ? AND c.parent_id IS NULL AND EXISTS (SELECT 1 FROM comment r WHERE r.parent_id = c.id)
                    """, now, memberId);
            jdbc.update("""
                    DELETE FROM comment c WHERE c.author_id = ? AND c.parent_id IS NULL
                      AND NOT EXISTS (SELECT 1 FROM comment r WHERE r.parent_id = c.id)
                    """, memberId);
            // 답글이 모두 사라진 남의 자리(삭제된 최상위)도 정리
            jdbc.update("""
                    DELETE FROM comment c WHERE c.deleted_at IS NOT NULL AND c.parent_id IS NULL AND c.post_id = ANY (?)
                      AND NOT EXISTS (SELECT 1 FROM comment r WHERE r.parent_id = c.id)
                    """, (Object) posts.toArray(new Long[0]));
            recount(posts);
        }

        private void recount(List<Long> posts) {
            if (posts.isEmpty()) {
                return;
            }
            jdbc.update("""
                    UPDATE post p SET comment_count = (SELECT count(*) FROM comment c
                                                       WHERE c.post_id = p.id AND c.deleted_at IS NULL AND c.hidden_at IS NULL)
                    WHERE p.id = ANY (?)
                    """, (Object) posts.toArray(new Long[0]));
        }
    }

    @Component
    public static class Likes implements WithdrawalPurgeStep {

        private final JdbcTemplate jdbc;

        public Likes(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public int order() {
            return 30;
        }

        @Override
        public void purge(long memberId) {
            List<Long> posts = jdbc.queryForList("DELETE FROM post_like WHERE member_id = ? RETURNING post_id", Long.class, memberId);
            if (!posts.isEmpty()) {
                jdbc.update("""
                        UPDATE post p SET like_count = (SELECT count(*) FROM post_like l WHERE l.post_id = p.id) WHERE p.id = ANY (?)
                        """, (Object) posts.toArray(new Long[0]));
            }
        }
    }

    @Component
    public static class Follows implements WithdrawalPurgeStep {

        private final FollowService followService;

        public Follows(FollowService followService) {
            this.followService = followService;
        }

        @Override
        public int order() {
            return 60;
        }

        @Override
        public void purge(long memberId) {
            followService.purgeWithdrawn(memberId);
        }
    }
}

package com.team.blog.account.application;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** account 모듈의 정리 단계: 50 로그인 수단 삭제(같은 이메일 새 가입 가능), 90 회원 정보 익명화(블로그 주소만 남김, 마지막). */
public final class MemberPurgeSteps {

    private MemberPurgeSteps() {
    }

    @Component
    public static class Identities implements WithdrawalPurgeStep {

        private final JdbcTemplate jdbc;

        public Identities(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public int order() {
            return 50;
        }

        @Override
        public void purge(long memberId) {
            jdbc.update("DELETE FROM auth_identity WHERE member_id = ?", memberId);
        }
    }

    @Component
    public static class Anonymize implements WithdrawalPurgeStep {

        private final JdbcTemplate jdbc;
        private final Clock clock;

        public Anonymize(JdbcTemplate jdbc, Clock clock) {
            this.jdbc = jdbc;
            this.clock = clock;
        }

        @Override
        public int order() {
            return 90;
        }

        @Override
        public void purge(long memberId) {
            Timestamp now = Timestamp.from(clock.instant());
            jdbc.update("DELETE FROM member_agreement WHERE member_id = ? AND type = 'AI'", memberId);
            jdbc.update("DELETE FROM notification_mute WHERE member_id = ?", memberId);
            jdbc.update("""
                    UPDATE member_suspension SET reason = '', ends_at = NULL, lifted_at = COALESCE(lifted_at, ?) WHERE member_id = ?
                    """, now, memberId);
            jdbc.update("""
                    UPDATE member SET nickname = NULL, bio = NULL, profile_image_id = NULL, profile_image_url = NULL,
                                      nickname_changed_at = NULL, deleted_at = ?, updated_at = ?
                    WHERE id = ?
                    """, now, now, memberId);
        }
    }
}

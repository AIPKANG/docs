package com.team.blog.support;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 001 가입 흐름 없이 {@code member} 행을 만든다.
 * {@code ck_member_withdrawn}, {@code ck_member_deleted}, {@code ck_member_nickname_null}을 만족하게 만든다.
 */
@TestComponent
public class MemberFixtures {

    private final JdbcTemplate jdbc;

    public MemberFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 정상 회원. */
    public long active(String handle, String nickname) {
        return insert(handle, nickname, "ACTIVE", null, null);
    }

    /** 탈퇴 유예 회원({@code status='WITHDRAWN'}, {@code withdrawn_at} 설정, 닉네임 유지). */
    public long withdrawing(String handle, String nickname, Instant withdrawnAt) {
        return insert(handle, nickname, "WITHDRAWN", withdrawnAt, null);
    }

    /** 익명 처리된 회원({@code nickname=NULL}, {@code deleted_at} 설정, {@code handle} 유지). */
    public long anonymized(String handle, Instant withdrawnAt, Instant deletedAt) {
        return insert(handle, null, "WITHDRAWN", withdrawnAt, deletedAt);
    }

    public void setNicknameChangedAt(long memberId, Instant at) {
        jdbc.update("UPDATE member SET nickname_changed_at = ? WHERE id = ?", at == null ? null : Timestamp.from(at), memberId);
    }

    private long insert(String handle, String nickname, String status, Instant withdrawnAt, Instant deletedAt) {
        Long id = jdbc.queryForObject("""
                INSERT INTO member (handle, nickname, status, withdrawn_at, deleted_at)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, handle, nickname, status,
                withdrawnAt == null ? null : Timestamp.from(withdrawnAt),
                deletedAt == null ? null : Timestamp.from(deletedAt));
        return id;
    }
}

package com.team.blog.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 001 가입 흐름 없이 {@code member} 행을 만든다.
 * {@code ck_member_withdrawn}, {@code ck_member_deleted}, {@code ck_member_nickname_null}을 만족하게 만든다.
 */
@TestComponent
public class MemberFixtures {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public MemberFixtures(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    // ----- 001-auth: 로그인 수단·정지 -----

    /** 이메일 가입 회원(정상 상태) + LOCAL 로그인 수단. {@code verified}면 인증 완료. */
    public long localMember(String handle, String nickname, String email, String rawPassword, boolean verified) {
        long id = active(handle, nickname);
        addLocalIdentity(id, email, rawPassword, verified ? Instant.now() : null);
        return id;
    }

    public void addLocalIdentity(long memberId, String email, String rawPassword, Instant verifiedAt) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO auth_identity (member_id, provider, provider_user_id, email, password_hash, email_verified_at)
                VALUES (?, 'LOCAL', ?, ?, ?, ?)
                """, memberId, normalized, normalized, passwordEncoder.encode(rawPassword),
                verifiedAt == null ? null : Timestamp.from(verifiedAt));
    }

    public void addSocialIdentity(long memberId, String provider, String providerUserId, String email, Instant verifiedAt) {
        jdbc.update("""
                INSERT INTO auth_identity (member_id, provider, provider_user_id, email, email_verified_at)
                VALUES (?, ?, ?, ?, ?)
                """, memberId, provider, providerUserId, email, verifiedAt == null ? null : Timestamp.from(verifiedAt));
    }

    /** 정지 이력 한 행. {@code endsAt}이 null이면 영구. 정지한 관리자 회원을 함께 만든다. */
    public long suspend(long memberId, String reason, Instant startedAt, Instant endsAt) {
        Long adminId = jdbc.query("SELECT id FROM member WHERE handle = 'fixture_admin'",
                        (rs, i) -> rs.getLong(1)).stream().findFirst()
                .orElseGet(() -> active("fixture_admin", "운영테스터"));
        return insertSuspension(memberId, reason, startedAt, endsAt, adminId);
    }

    public void setStatus(long memberId, String status) {
        jdbc.update("UPDATE member SET status = ? WHERE id = ?", status, memberId);
    }

    private long insertSuspension(long memberId, String reason, Instant startedAt, Instant endsAt, Long adminId) {
        return jdbc.queryForObject("""
                INSERT INTO member_suspension (member_id, reason, started_at, ends_at, suspended_by)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, memberId, reason, Timestamp.from(startedAt),
                endsAt == null ? null : Timestamp.from(endsAt), adminId);
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

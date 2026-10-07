package com.team.blog.account.infra;

import com.team.blog.shared.security.AccountStatusLookup;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@link AccountStatusLookup} 구현: {@code member} PK + {@code auth_identity}({@code uq_auth_identity_member}) 한 번 조회. */
@Component
public class JdbcAccountStatusLookup implements AccountStatusLookup {

    private final JdbcTemplate jdbc;

    public JdbcAccountStatusLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WriteStatus> find(long memberId) {
        List<WriteStatus> rows = jdbc.query("""
                SELECT m.status, m.deleted_at IS NOT NULL AS anonymized, a.email_verified_at IS NOT NULL AS verified
                FROM member m LEFT JOIN auth_identity a ON a.member_id = m.id
                WHERE m.id = ?
                """, (rs, i) -> new WriteStatus(
                "WITHDRAWN".equals(rs.getString("status")) && !rs.getBoolean("anonymized"),
                rs.getBoolean("anonymized"),
                rs.getBoolean("verified")), memberId);
        return rows.stream().findFirst();
    }
}

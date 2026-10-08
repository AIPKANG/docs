package com.team.blog.moderation.application;

import com.team.blog.shared.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 관리자 화면 조회(022 FR-012~FR-015). 대기 탭은 대상별 묶음을 신고 수 많은 순 → 최근 신고 순, 처리됨 탭은 처리 시각 최신순.
 * 내용은 신고 시점 복사본만 보여 주고 지금 원문은 열지 않는다(관리자도 남의 비공개 원문을 못 봄).
 */
@Service
public class ModerationQuery {

    public record CaseRow(long id, String targetType, int reportCount, Instant lastReportedAt, String snapshotTitle,
                          String snapshotContent, String status, Instant handledAt, boolean targetHidden) {
    }

    public record ReportRow(String reasonLabel, String detail, Instant createdAt) {
    }

    public record AuthorInfo(long id, String handle, String nickname, Instant joinedAt, long hiddenCount,
                             List<SuspensionRow> suspensions) {

        public String joinedLabel() {
            return com.team.blog.shared.web.KoreanDateFormatter.yearMonthDay(joinedAt);
        }
    }

    public record SuspensionRow(String reason, Instant startedAt, Instant endsAt, Instant liftedAt) {
    }

    /** @param currentState 공개 / 비공개 / 휴지통 / 이미 숨김 / 작성자 탈퇴 / 대상 없음 */
    public record CaseDetail(CaseRow row, List<ReportRow> reports, String currentState, AuthorInfo author) {
    }

    public record MemberRow(long id, String handle, String nickname, String role, Instant joinedAt, boolean suspended,
                            Instant suspendedUntil, boolean permanent) {

        public String suspendedLabel() {
            return permanent ? "영구 정지" : "정지 ~" + com.team.blog.shared.web.KoreanDateFormatter.dateTime(suspendedUntil);
        }
    }

    private final JdbcTemplate jdbc;
    private final AdminGuard adminGuard;

    public ModerationQuery(JdbcTemplate jdbc, AdminGuard adminGuard) {
        this.jdbc = jdbc;
        this.adminGuard = adminGuard;
    }

    private static final String CASE_SELECT = """
            SELECT c.id, c.target_type, c.snapshot_title, c.snapshot_content, c.status, c.handled_at,
                   (SELECT count(*) FROM report r WHERE r.case_id = c.id) AS cnt,
                   (SELECT max(r.created_at) FROM report r WHERE r.case_id = c.id) AS last_at,
                   COALESCE((SELECT p.hidden_at IS NOT NULL FROM post p WHERE p.id = c.post_id),
                            (SELECT m.hidden_at IS NOT NULL FROM comment m WHERE m.id = c.comment_id), false) AS target_hidden
            FROM report_case c
            """;

    private CaseRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CaseRow(rs.getLong("id"), rs.getString("target_type"), rs.getInt("cnt"),
                rs.getTimestamp("last_at") == null ? null : rs.getTimestamp("last_at").toInstant(), rs.getString("snapshot_title"),
                rs.getString("snapshot_content"), rs.getString("status"),
                rs.getTimestamp("handled_at") == null ? null : rs.getTimestamp("handled_at").toInstant(), rs.getBoolean("target_hidden"));
    }

    public List<CaseRow> pending(Optional<CurrentUser> current) {
        adminGuard.requireAdmin(current);
        return jdbc.query(CASE_SELECT + " WHERE c.status = 'PENDING' ORDER BY cnt DESC, last_at DESC, c.id DESC LIMIT 100",
                (rs, n) -> row(rs));
    }

    public List<CaseRow> done(Optional<CurrentUser> current) {
        adminGuard.requireAdmin(current);
        return jdbc.query(CASE_SELECT + " WHERE c.status <> 'PENDING' ORDER BY c.closed_at DESC NULLS LAST, c.id DESC LIMIT 100",
                (rs, n) -> row(rs));
    }

    public Optional<CaseDetail> detail(Optional<CurrentUser> current, long caseId) {
        adminGuard.requireAdmin(current);
        Optional<CaseRow> row = jdbc.query(CASE_SELECT + " WHERE c.id = ?", (rs, n) -> row(rs), caseId).stream().findFirst();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        List<ReportRow> reports = jdbc.query("SELECT reason, detail, created_at FROM report WHERE case_id = ? ORDER BY created_at DESC",
                (rs, n) -> new ReportRow(ReportReason.labelOf(rs.getString(1)), rs.getString(2), rs.getTimestamp(3).toInstant()), caseId);
        String state = jdbc.queryForObject("""
                SELECT CASE
                    WHEN c.post_id IS NULL AND c.comment_id IS NULL THEN '대상 없음'
                    WHEN am.withdrawn_at IS NOT NULL THEN '작성자 탈퇴'
                    WHEN COALESCE(p.hidden_at, cm.hidden_at) IS NOT NULL THEN '이미 숨김'
                    WHEN COALESCE(p.deleted_at, cp.deleted_at) IS NOT NULL OR cm.deleted_at IS NOT NULL THEN '휴지통'
                    WHEN COALESCE(p.visibility, cp.visibility) = 'PRIVATE' THEN '비공개'
                    ELSE '공개' END
                FROM report_case c
                JOIN member am ON am.id = c.target_author_id
                LEFT JOIN post p ON p.id = c.post_id
                LEFT JOIN comment cm ON cm.id = c.comment_id
                LEFT JOIN post cp ON cp.id = cm.post_id
                WHERE c.id = ?
                """, String.class, caseId);
        AuthorInfo author = jdbc.queryForObject("""
                SELECT m.id, m.handle, m.nickname, m.created_at,
                       (SELECT count(*) FROM post p WHERE p.author_id = m.id AND p.hidden_at IS NOT NULL)
                     + (SELECT count(*) FROM comment x WHERE x.author_id = m.id AND x.hidden_at IS NOT NULL) AS hidden_count
                FROM report_case c JOIN member m ON m.id = c.target_author_id WHERE c.id = ?
                """, (rs, n) -> new AuthorInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(),
                rs.getLong(5), List.of()), caseId);
        List<SuspensionRow> suspensions = jdbc.query("""
                SELECT reason, started_at, ends_at, lifted_at FROM member_suspension WHERE member_id = ? ORDER BY started_at DESC
                """, (rs, n) -> new SuspensionRow(rs.getString(1), rs.getTimestamp(2).toInstant(),
                rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant(),
                rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant()), author.id());
        return Optional.of(new CaseDetail(row.get(), reports, state,
                new AuthorInfo(author.id(), author.handle(), author.nickname(), author.joinedAt(), author.hiddenCount(), suspensions)));
    }

    public List<MemberRow> members(Optional<CurrentUser> current, String q) {
        adminGuard.requireAdmin(current);
        String pattern = "%" + (q == null ? "" : q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        return jdbc.query("""
                SELECT m.id, m.handle, m.nickname, m.role, m.created_at, s.ends_at, s.id AS sid
                FROM member m
                LEFT JOIN LATERAL (SELECT id, ends_at FROM member_suspension x WHERE x.member_id = m.id AND x.lifted_at IS NULL
                                   AND (x.ends_at IS NULL OR x.ends_at > now()) ORDER BY started_at DESC LIMIT 1) s ON TRUE
                WHERE m.deleted_at IS NULL AND (m.handle ILIKE ? ESCAPE '\\' OR m.nickname ILIKE ? ESCAPE '\\')
                ORDER BY m.id DESC LIMIT 50
                """, (rs, n) -> new MemberRow(rs.getLong("id"), rs.getString("handle"), rs.getString("nickname"), rs.getString("role"),
                rs.getTimestamp("created_at").toInstant(), rs.getObject("sid") != null,
                rs.getTimestamp("ends_at") == null ? null : rs.getTimestamp("ends_at").toInstant(),
                rs.getObject("sid") != null && rs.getTimestamp("ends_at") == null), pattern, pattern);
    }
}

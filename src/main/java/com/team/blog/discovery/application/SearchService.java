package com.team.blog.discovery.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.post.application.PostAccessPolicy;
import com.team.blog.post.application.PostCard;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.RateLimitedException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 글·사람 검색(020, 33). 대상은 006 공용 목록 조건 + 관리자 숨김 아님. 관련도순은 ① 모든 단어가 제목 ② 제목·태그 ③ 본문까지의
 * 단계 순서, 단계 안은 최신순. 단계마다 먼저 최근 글 {@code recent-window}개 안에서 찾고, 모자라면 가장 긴 단어로 제목·태그·본문
 * 후보를 따로 모아(트라이그램 인덱스) 전체 조건을 확인한다(33 §4). 커서 {@code {단계}:{공개 시각}:{id}}.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    public static final String NOTICE_TWO_CHAR = "TWO_CHAR_TITLE_TAG_ONLY";

    public record SearchCard(long id, String url, String title, String excerpt, String snippetHtml,
                             List<SearchSnippet.Part> snippetParts, String thumbnailUrl, Instant firstPublicAt,
                             int commentCount, int likeCount, PostCard.Author author) {
    }

    public record SearchPage(List<SearchCard> items, String nextCursor, String notice) {
    }

    public record Person(String handle, String nickname, String profileImageUrl, String bio) {

        public com.team.blog.account.application.ProfileAvatar avatar() {
            return new com.team.blog.account.application.ProfileAvatar(handle, nickname, profileImageUrl);
        }
    }

    private record Row(SearchCard card, int stage) {
    }

    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;
    private final RedisRateLimiter rateLimiter;
    private final SearchProperties properties;

    public SearchService(JdbcTemplate jdbc, PostAccessPolicy accessPolicy, RedisRateLimiter rateLimiter,
                         SearchProperties properties) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /** 같은 방문자 1분 30번(FR-018). 제한 저장소가 멈추면 열어 둔다. */
    public void limit(String visitorKey) {
        try {
            RedisRateLimiter.Result r = rateLimiter.tryAcquire(RedisRateLimiter.key("search:visitor", visitorKey),
                    properties.perMinute(), Duration.ofMinutes(1));
            if (!r.allowed()) {
                throw new RateLimitedException(r.retryAfterSeconds());
            }
        } catch (DataAccessException e) {
            // 공통 원칙 5
        }
    }

    /**
     * @param authorId 블로그 안 검색이면 그 주인, 아니면 null
     * @param latest   최신순이면 true(단계 0 하나)
     */
    public SearchPage posts(SearchTerms terms, boolean latest, Long authorId, String cursor) {
        long started = System.nanoTime();
        String notice = terms.hasTwoCharWord() ? NOTICE_TWO_CHAR : null;
        if (terms.empty()) {
            return new SearchPage(List.of(), null, notice);
        }
        int stage = latest ? 0 : 1;
        Instant afterAt = null;
        long afterId = 0;
        if (cursor != null && !cursor.isEmpty()) {
            String[] parts = cursor.split(":", 2);
            int lastColon = cursor.lastIndexOf(':');
            try {
                stage = Integer.parseInt(parts[0]);
                afterAt = Instant.parse(cursor.substring(parts[0].length() + 1, lastColon));
                afterId = Long.parseLong(cursor.substring(lastColon + 1));
            } catch (RuntimeException e) {
                throw new PostContentException("INVALID_CURSOR");
            }
            if (latest ? stage != 0 : stage < 1 || stage > 3) {
                throw new PostContentException("INVALID_CURSOR");
            }
        }
        int want = properties.pageSize() + 1;
        List<Row> rows = new ArrayList<>();
        int lastStage = latest ? 0 : 3;
        for (int s = stage; s <= lastStage && rows.size() < want; s++) {
            boolean continuing = s == stage && afterAt != null;
            for (SearchCard card : stage(terms, s, authorId, continuing ? afterAt : null, afterId, want - rows.size())) {
                rows.add(new Row(card, s));
            }
        }
        String next = null;
        if (rows.size() > properties.pageSize()) {
            rows = rows.subList(0, properties.pageSize());
            Row last = rows.get(rows.size() - 1);
            next = last.stage() + ":" + last.card().firstPublicAt() + ":" + last.card().id();
        }
        log.info("search q.length={} took={}ms", terms.text().length(), (System.nanoTime() - started) / 1_000_000);
        return new SearchPage(rows.stream().map(Row::card).toList(), next, notice);
    }

    private String titleHas() {
        return "p.title ILIKE ? ESCAPE '\\'";
    }

    private String tagHas() {
        return "EXISTS (SELECT 1 FROM post_tag pt JOIN tag g ON g.id = pt.tag_id WHERE pt.post_id = p.id AND g.name ILIKE ? ESCAPE '\\')";
    }

    private String bodyHas() {
        return "p.content_md ILIKE ? ESCAPE '\\'";
    }

    /** 단계 조건과 그 인자. 0 = 어딘가에 모두(최신순), 1 = 제목에 모두, 2 = ①이 아니고 제목·태그에 모두, 3 = ①②가 아니고 어딘가에 모두. */
    private void stageCondition(SearchTerms terms, int stage, StringBuilder sql, List<Object> args) {
        StringBuilder all = new StringBuilder();
        List<Object> allArgs = new ArrayList<>();
        StringBuilder titles = new StringBuilder();
        List<Object> titleArgs = new ArrayList<>();
        StringBuilder titleTags = new StringBuilder();
        List<Object> titleTagArgs = new ArrayList<>();
        for (String w : terms.words()) {
            String pattern = SearchTerms.likePattern(w);
            String tagPattern = SearchTerms.likePattern(w.toLowerCase(Locale.ROOT));
            titles.append(titles.isEmpty() ? "" : " AND ").append(titleHas());
            titleArgs.add(pattern);
            titleTags.append(titleTags.isEmpty() ? "" : " AND ").append("(").append(titleHas()).append(" OR ").append(tagHas()).append(")");
            titleTagArgs.add(pattern);
            titleTagArgs.add(tagPattern);
            all.append(all.isEmpty() ? "" : " AND ").append("(").append(titleHas()).append(" OR ").append(tagHas());
            allArgs.add(pattern);
            allArgs.add(tagPattern);
            if (SearchTerms.searchesBody(w)) {
                all.append(" OR ").append(bodyHas());
                allArgs.add(pattern);
            }
            all.append(")");
        }
        switch (stage) {
            case 1 -> {
                sql.append(" AND (").append(titles).append(")");
                args.addAll(titleArgs);
            }
            case 2 -> {
                sql.append(" AND NOT (").append(titles).append(") AND (").append(titleTags).append(")");
                args.addAll(titleArgs);
                args.addAll(titleTagArgs);
            }
            case 3 -> {
                sql.append(" AND NOT (").append(titleTags).append(") AND (").append(all).append(")");
                args.addAll(titleTagArgs);
                args.addAll(allArgs);
            }
            default -> {
                sql.append(" AND (").append(all).append(")");
                args.addAll(allArgs);
            }
        }
    }

    private List<SearchCard> stage(SearchTerms terms, int stage, Long authorId, Instant afterAt, long afterId, int limit) {
        // ① 최근창
        StringBuilder sql = new StringBuilder("SELECT * FROM (").append(base(authorId, afterAt, afterId, new ArrayList<>(), true))
                .append(") p WHERE TRUE");
        List<Object> args = new ArrayList<>();
        base(authorId, afterAt, afterId, args, true);
        stageCondition(terms, stage, sql, args);
        sql.append(" ORDER BY p.first_public_at DESC, p.id DESC LIMIT ").append(limit);
        List<SearchCard> found = jdbc.query(sql.toString(), (rs, n) -> card(rs, terms), args.toArray());
        if (found.size() >= limit || windowCount(authorId, afterAt, afterId) < properties.recentWindow()) {
            return found;
        }
        // ② 인덱스: 가장 긴 단어로 제목·태그·본문 후보를 따로 모은다(태그 EXISTS를 OR로 섞으면 본문 인덱스를 못 쓴다)
        String longest = terms.words().stream().max(java.util.Comparator.comparingInt(String::length)).orElseThrow();
        List<Object> indexArgs = new ArrayList<>();
        String scope = base(authorId, afterAt, afterId, indexArgs, false);
        StringBuilder candidates = new StringBuilder("SELECT id FROM post WHERE title ILIKE ? ESCAPE '\\'"
                + " UNION SELECT pt.post_id FROM post_tag pt JOIN tag g ON g.id = pt.tag_id WHERE g.name ILIKE ? ESCAPE '\\'");
        indexArgs.add(SearchTerms.likePattern(longest));
        indexArgs.add(SearchTerms.likePattern(longest.toLowerCase(Locale.ROOT)));
        if (SearchTerms.searchesBody(longest) && (stage == 0 || stage == 3)) {
            candidates.append(" UNION SELECT id FROM post WHERE content_md ILIKE ? ESCAPE '\\'");
            indexArgs.add(SearchTerms.likePattern(longest));
        }
        StringBuilder sql2 = new StringBuilder("SELECT * FROM (").append(scope).append(" AND p.id IN (").append(candidates)
                .append(")) p WHERE TRUE");
        stageCondition(terms, stage, sql2, indexArgs);
        sql2.append(" ORDER BY p.first_public_at DESC, p.id DESC LIMIT ").append(limit);
        return jdbc.query(sql2.toString(), (rs, n) -> card(rs, terms), indexArgs.toArray());
    }

    /** 공용 조건 + 숨김 아님 + 블로그 + 커서. {@code window}면 최근창 LIMIT까지. */
    private String base(Long authorId, Instant afterAt, long afterId, List<Object> args, boolean window) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.content_md, p.excerpt, p.thumbnail_url, p.first_public_at, p.comment_count, p.like_count,
                       m.handle, m.nickname, m.profile_image_url
                FROM post p JOIN member m ON m.id = p.author_id
                WHERE """).append(' ').append(accessPolicy.publicListingCondition("p", "m")).append(" AND p.hidden_at IS NULL");
        if (authorId != null) {
            sql.append(" AND p.author_id = ?");
            args.add(authorId);
        }
        if (afterAt != null) {
            sql.append(" AND (p.first_public_at, p.id) < (?, ?)");
            args.add(Timestamp.from(afterAt));
            args.add(afterId);
        }
        if (window) {
            sql.append(" ORDER BY p.first_public_at DESC, p.id DESC LIMIT ").append(properties.recentWindow());
        }
        return sql.toString();
    }

    private int windowCount(Long authorId, Instant afterAt, long afterId) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT count(*) FROM (" + base(authorId, afterAt, afterId, args, true) + ") w";
        Integer n = jdbc.queryForObject(sql, Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static SearchCard card(ResultSet rs, SearchTerms terms) throws SQLException {
        String handle = rs.getString("handle");
        long id = rs.getLong("id");
        SearchSnippet.Result snippet = SearchSnippet.of(rs.getString("content_md"), rs.getString("title"), terms.words());
        String plain = snippet.parts().stream().map(SearchSnippet.Part::text).reduce("", String::concat);
        return new SearchCard(id, "/@" + handle + "/posts/" + id, rs.getString("title"), plain, snippet.html(),
                snippet.parts(), rs.getString("thumbnail_url"), rs.getTimestamp("first_public_at").toInstant(),
                rs.getInt("comment_count"), rs.getInt("like_count"),
                new PostCard.Author(handle, rs.getString("nickname"), rs.getString("profile_image_url")));
    }

    /** 사람 검색(FR-016): 닉네임·주소 부분 일치, 정확히 일치하면 먼저, 탈퇴 유예 제외, 최대 20명. */
    public List<Person> people(SearchTerms terms) {
        if (terms.empty()) {
            return List.of();
        }
        String q = terms.words().get(0);
        String lower = q.toLowerCase(Locale.ROOT);
        return jdbc.query("""
                SELECT handle, nickname, profile_image_url, bio FROM member
                WHERE withdrawn_at IS NULL AND (nickname ILIKE ? ESCAPE '\\' OR handle LIKE ? ESCAPE '\\')
                ORDER BY (lower(nickname) = ? OR handle = ?) DESC, id DESC
                LIMIT ?
                """, (rs, n) -> new Person(rs.getString("handle"), rs.getString("nickname"), rs.getString("profile_image_url"),
                firstLine(rs.getString("bio"))), SearchTerms.likePattern(q), SearchTerms.likePattern(lower), lower, lower,
                properties.peopleLimit());
    }

    private static String firstLine(String bio) {
        return bio == null || bio.isBlank() ? null : bio.strip().split("\\R", 2)[0];
    }
}

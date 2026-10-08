package com.team.blog.discovery.web;

import com.team.blog.post.application.PostAccessPolicy;
import com.team.blog.post.markdown.MarkdownProperties;
import java.time.format.DateTimeFormatter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * 검색 엔진용 sitemap·robots(02 §6, 06 §3). 공용 목록 조건(공개·발행·삭제 안 됨·숨김 아님·작성자 탈퇴 아님)인 글과 그 작성자 블로그만 —
 * 비공개·친구 공개·링크 공개 글은 들어갈 길이 없다. 한 파일 상한 50,000개(sitemap 규약).
 */
@RestController
public class SitemapController {

    private static final int MAX_URLS = 50_000;

    private final JdbcTemplate jdbc;
    private final PostAccessPolicy accessPolicy;
    private final String origin;

    public SitemapController(JdbcTemplate jdbc, PostAccessPolicy accessPolicy, MarkdownProperties markdownProperties) {
        this.jdbc = jdbc;
        this.accessPolicy = accessPolicy;
        this.origin = markdownProperties.siteOrigin().replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(xml, "/", null);
        jdbc.query("""
                SELECT DISTINCT m.handle FROM post p JOIN member m ON m.id = p.author_id WHERE """ + " "
                + accessPolicy.publicListingCondition("p", "m") + " ORDER BY m.handle LIMIT 10000",
                rs -> { url(xml, "/@" + rs.getString("handle"), null); });
        jdbc.query("""
                SELECT p.id, m.handle, COALESCE(p.edited_at, p.first_public_at) AS changed
                FROM post p JOIN member m ON m.id = p.author_id WHERE """ + " " + accessPolicy.publicListingCondition("p", "m")
                + " ORDER BY p.first_public_at DESC, p.id DESC LIMIT " + MAX_URLS / 2,
                rs -> {
                    url(xml, "/@" + rs.getString("handle") + "/posts/" + rs.getLong("id"),
                            DateTimeFormatter.ISO_INSTANT.format(rs.getTimestamp("changed").toInstant()));
                });
        xml.append("</urlset>\n");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml.toString());
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nDisallow: /api/\nDisallow: /settings\nDisallow: /manage/\nDisallow: /admin/\nDisallow: /write\n"
                + "Sitemap: " + origin + "/sitemap.xml\n";
    }

    private void url(StringBuilder xml, String path, String lastmod) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(origin + path)).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(lastmod).append("</lastmod>");
        }
        xml.append("</url>\n");
    }
}

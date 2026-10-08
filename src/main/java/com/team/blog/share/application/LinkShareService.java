package com.team.blog.share.application;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 링크 공개 열쇠(029). 열쇠는 처음 필요할 때 만들고, 다시 만들면 예전 주소로는 읽을 수 없다. */
@Service
public class LinkShareService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    public LinkShareService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 글의 열쇠(없으면 만든다). */
    public String token(long postId) {
        jdbc.update("INSERT INTO post_link_share (post_id, token) VALUES (?, ?) ON CONFLICT (post_id) DO NOTHING",
                postId, newToken());
        return jdbc.queryForObject("SELECT token FROM post_link_share WHERE post_id = ?", String.class, postId);
    }

    /** 새 열쇠로 바꾼다(예전 주소는 막힘). */
    public String regenerate(long postId) {
        String token = newToken();
        jdbc.update("""
                INSERT INTO post_link_share (post_id, token) VALUES (?, ?)
                ON CONFLICT (post_id) DO UPDATE SET token = EXCLUDED.token, created_at = now()
                """, postId, token);
        return token;
    }

    public boolean matches(long postId, String key) {
        if (key == null || key.isEmpty() || key.length() > 64) {
            return false;
        }
        List<String> tokens = jdbc.queryForList("SELECT token FROM post_link_share WHERE post_id = ?", String.class, postId);
        return !tokens.isEmpty() && java.security.MessageDigest.isEqual(tokens.get(0).getBytes(), key.getBytes());
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

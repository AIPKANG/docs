package com.team.blog.account.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Redis 키에 원문 대신 넣는 SHA-256 해시(소문자 16진수). 토큰·이메일 원문을 키에 넣지 않는다(FR-014). */
public final class KeyHashing {

    private KeyHashing() {
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 이메일은 trim + 소문자로 정규화한 뒤 해시한다. */
    public static String emailHash(String email) {
        return sha256(email == null ? "" : email.strip().toLowerCase(Locale.ROOT));
    }
}

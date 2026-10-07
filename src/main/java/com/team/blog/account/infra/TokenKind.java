package com.team.blog.account.infra;

/** 1회용 링크 토큰 종류와 Redis 키 접두어(contracts/redis-keys.md). */
public enum TokenKind {
    /** 이메일 인증(24h). */
    VERIFY("auth:verify:", "auth:verify-current:"),
    /** 비밀번호 재설정(30m). */
    RESET("auth:reset:", "auth:reset-current:");

    private final String tokenPrefix;
    private final String currentPrefix;

    TokenKind(String tokenPrefix, String currentPrefix) {
        this.tokenPrefix = tokenPrefix;
        this.currentPrefix = currentPrefix;
    }

    public String tokenKey(String tokenHash) {
        return tokenPrefix + tokenHash;
    }

    public String currentKey(long memberId) {
        return currentPrefix + memberId;
    }

    String tokenPrefix() {
        return tokenPrefix;
    }
}

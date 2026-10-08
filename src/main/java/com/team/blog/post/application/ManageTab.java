package com.team.blog.post.application;

import java.util.Locale;

/** 내 글 관리 탭(41 §2). 기본은 임시글. */
public enum ManageTab {
    DRAFTS, PUBLISHED, TRASH;

    public static ManageTab of(String raw) {
        if (raw == null) {
            return DRAFTS;
        }
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DRAFTS;
        }
    }

    public String param() {
        return name().toLowerCase(Locale.ROOT);
    }
}

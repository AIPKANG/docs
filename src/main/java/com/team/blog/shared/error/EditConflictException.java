package com.team.blog.shared.error;

import java.time.Instant;

/**
 * 편집 버전 충돌 → 409 {@code EDIT_CONFLICT} + 서버 쪽 현재 내용(004 FR-017). 서버는 어느 쪽도 덮어쓰지 않았다.
 */
public class EditConflictException extends RuntimeException {

    public static final String CODE = "EDIT_CONFLICT";

    private final ErrorResponse.ServerContent server;

    public EditConflictException(String title, String contentMd, long version, Instant savedAt) {
        super(CODE);
        this.server = new ErrorResponse.ServerContent(title, contentMd, version, savedAt);
    }

    public ErrorResponse.ServerContent getServer() {
        return server;
    }
}

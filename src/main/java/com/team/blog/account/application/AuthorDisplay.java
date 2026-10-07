package com.team.blog.account.application;

import java.time.Instant;

/**
 * {@code 닉네임 @블로그주소} 표시 규칙(research R-16, FR-027). 탈퇴(유예·익명 처리)한 회원은 "탈퇴한 사용자"만 보이고 링크가 없다.
 * 목록·댓글 읽기 쿼리가 가져온 {@code m.handle, m.nickname, m.withdrawn_at}으로 {@link #of}를 만든다.
 * 템플릿 조각: {@code templates/fragments/author.html}.
 */
public record AuthorDisplay(String handle, String nickname, boolean withdrawn) {

    public static final String WITHDRAWN_LABEL = "탈퇴한 사용자";

    public static AuthorDisplay of(String handle, String nickname, Instant withdrawnAt) {
        return new AuthorDisplay(handle, nickname, withdrawnAt != null || nickname == null);
    }

    /** 정상: {@code 김민서 @kim755030} / 탈퇴: {@code 탈퇴한 사용자}. */
    public String fullLabel() {
        return withdrawn ? WITHDRAWN_LABEL : nickname + " @" + handle;
    }

    /** 정상: {@code 김민서} / 탈퇴: {@code 탈퇴한 사용자}. */
    public String shortLabel() {
        return withdrawn ? WITHDRAWN_LABEL : nickname;
    }

    /** 정상: {@code /@kim755030} / 탈퇴: null(링크 없음). */
    public String blogPath() {
        return withdrawn ? null : "/@" + handle;
    }
}

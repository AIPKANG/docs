package com.team.blog.post.application.visibility;

import com.team.blog.post.domain.PostStatus;

/** 읽기 판정 입력(휴지통 글은 조회 단계에서 이미 빠진다). {@code authorWithdrawn}: 작성자가 탈퇴 유예·익명 처리됨. */
public record PostFacts(long id, long authorId, PostStatus status, String visibility, boolean authorWithdrawn) {
}

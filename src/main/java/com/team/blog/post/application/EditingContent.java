package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import java.time.Instant;

/**
 * 편집 화면에 보여줄 현재 내용(research R-2). 409 본문의 {@code server}도 이 값이다.
 *
 * @param editing 발행 글을 고치는 중(작업본 또는 더 새 버퍼가 있음)
 */
public record EditingContent(long postId, PostStatus status, String title, String contentMd, long version,
                             Instant savedAt, boolean editing) {
}

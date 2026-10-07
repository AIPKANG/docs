package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import java.time.Instant;

/** 내 글 최소 목록 한 줄(011이 넓힌다). {@code editing}이면 "수정 중". */
public record MyPostRow(long id, String title, PostStatus status, boolean editing, Instant updatedAt) {
}

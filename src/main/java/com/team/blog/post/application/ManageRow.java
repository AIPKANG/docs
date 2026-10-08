package com.team.blog.post.application;

import com.team.blog.post.domain.PostStatus;
import java.time.Instant;

/** 내 글 관리 한 줄(41 §3). 본문 칸은 읽지 않는다. {@code url}은 발행 글 보기 주소. */
public record ManageRow(long id, String title, PostStatus status, String visibility, boolean editing, boolean hidden,
                        Instant updatedAt, Instant publishedAt, Instant editedAt, Instant deletedAt, Instant purgeAt,
                        long viewCount, int likeCount, int commentCount, String url) {
}

package com.team.blog.post.application;

import java.time.Instant;

/** 서버 자동 저장 버퍼에 있는 글 하나의 최근 내용(04 §2-3 Hash). */
public record BufferedContent(long memberId, String title, String contentMd, long version, Instant savedAt) {
}

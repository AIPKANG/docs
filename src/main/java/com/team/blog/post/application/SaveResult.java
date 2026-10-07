package com.team.blog.post.application;

import java.time.Instant;

/** 저장 성공: 서버가 매긴 새 편집 버전과 저장 시각. */
public record SaveResult(long version, Instant savedAt) {
}

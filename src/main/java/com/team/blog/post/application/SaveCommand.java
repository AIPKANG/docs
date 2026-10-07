package com.team.blog.post.application;

/** 자동·수동 저장 입력(04 §2-3). 자동 저장 대상은 제목과 본문뿐이다(FR-003). */
public record SaveCommand(String title, String contentMd, Long baseVersion) {
}

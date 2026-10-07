package com.team.blog.post.application;

import java.util.List;

/** 발행 입력(05 §5). 작성자 값은 받지 않는다 — 로그인 정보로만 정한다(FR-021). */
public record PublishCommand(String title, String contentMd, List<String> tags, String visibility, Long baseVersion) {
}

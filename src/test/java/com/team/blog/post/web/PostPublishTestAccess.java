package com.team.blog.post.web;

import com.team.blog.post.application.PublishCommand;
import tools.jackson.databind.json.JsonMapper;

/** 테스트에서 발행 요청 본문을 컨트롤러와 같은 방식으로 읽는다(요청 요약 계산용). */
public final class PostPublishTestAccess {

    private PostPublishTestAccess() {
    }

    public static PublishCommand command(String json) {
        return PostPublishApiController.command(JsonMapper.builder().build().readTree(json));
    }
}

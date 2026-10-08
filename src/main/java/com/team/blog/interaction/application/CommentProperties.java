package com.team.blog.interaction.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 014 수치(헌법 II, 21 §12). */
@ConfigurationProperties("blog.comment")
public record CommentProperties(@DefaultValue("1000") int maxLength,
                                @DefaultValue("20") int pageSize,
                                @DefaultValue("3") int previewReplies,
                                @DefaultValue("10") int createPerMinute,
                                @DefaultValue("20") int editPerMinute,
                                @DefaultValue("10s") Duration dedupeWindow,
                                /** 답글 깊이(028): 공통 1(최상위 아래 1단계), 0이면 무제한(강성찬 개인 확장). */
                                @DefaultValue("1") int maxDepth) {

    /** 답글을 바로 위 댓글에 다는 구조인지(028 강성찬 개인 확장). */
    public boolean nested() {
        return maxDepth != 1;
    }
}

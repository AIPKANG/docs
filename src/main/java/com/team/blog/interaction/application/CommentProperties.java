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
                                @DefaultValue("10s") Duration dedupeWindow) {
}

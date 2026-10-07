package com.team.blog.post.markdown;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 007 수치(헌법 II, 12 §7).
 *
 * @param maxNesting       목록·인용 중첩 한도
 * @param renderTimeout    렌더링 전체 시간 한도
 * @param siteOrigin       우리 사이트 주소(이 주소로 시작하는 링크는 같은 탭)
 * @param previewPerMinute 미리보기 사용자당 1분 횟수
 */
@ConfigurationProperties("blog.markdown")
public record MarkdownProperties(@DefaultValue("20") int maxNesting,
                                 @DefaultValue("1s") Duration renderTimeout,
                                 @DefaultValue("http://localhost:8080") String siteOrigin,
                                 @DefaultValue("60") int previewPerMinute,
                                 @DefaultValue Rerender rerender) {

    /** 다시 렌더링 배치(12 §7-7). */
    public record Rerender(@DefaultValue("true") boolean enabled,
                           @DefaultValue("0 10 5 * * *") String cron,
                           @DefaultValue("100") int batchSize) {
    }
}

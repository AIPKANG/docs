package com.team.blog.tag.application.suggest;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 태그 추천 설정(34 §2, FR-034). 한도·모델·시간 제한·글자 수·동시 처리 수는 모두 설정값이고, Gemini 키는 환경 변수로만 넣는다.
 */
@ConfigurationProperties("blog.ai.tag-suggest")
public record AiTagProperties(@DefaultValue("true") boolean enabled,
                              @DefaultValue Gemini gemini,
                              @DefaultValue Ollama ollama,
                              @DefaultValue("20") int perUserDaily,
                              @DefaultValue("100") int minChars,
                              @DefaultValue("5") int maxSuggestions,
                              @DefaultValue("50") int popularCount,
                              @DefaultValue("1") int promptVersion,
                              @DefaultValue("30d") Duration exactCacheTtl,
                              @DefaultValue("7d") Duration similarCacheTtl,
                              @DefaultValue("0.9") double similarity,
                              @DefaultValue("60s") Duration cooldown) {

    /** @param resetZone 하루 한도가 초기화되는 기준 시간대(Gemini는 미국 태평양 자정) */
    public record Gemini(@DefaultValue("") String apiKey,
                         @DefaultValue("gemini-flash-lite-latest") String model,
                         @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
                         @DefaultValue("10s") Duration timeout,
                         @DefaultValue("8000") int maxChars,
                         @DefaultValue("450") int dailyLimit,
                         @DefaultValue("America/Los_Angeles") String resetZone) {
    }

    public record Ollama(@DefaultValue("http://ollama:11434") String baseUrl,
                         @DefaultValue("qwen2.5:1.5b") String model,
                         @DefaultValue("30s") Duration timeout,
                         @DefaultValue("2000") int maxChars,
                         @DefaultValue("1") int maxConcurrency) {
    }
}

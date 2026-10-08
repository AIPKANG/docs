package com.team.blog.tag.infra;

import com.team.blog.tag.application.suggest.AiTagProperties;
import com.team.blog.tag.application.suggest.TagSuggester;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google Gemini API(무료 등급, 34 §2·§3). 키는 헤더 {@code x-goog-api-key}로만 보내 주소·로그에 남지 않는다. JSON 스키마
 * {@code {"tags":[string] ≤ 5}}로 출력 형식을 강제하고 최대 출력 토큰은 100. 429는 본문의 한도 이름으로 하루·분당을 가른다.
 */
@Component
public class GeminiTagSuggester implements TagSuggester {

    private final AiTagProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient client;

    public GeminiTagSuggester(AiTagProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.gemini().timeout()).build());
        factory.setReadTimeout(properties.gemini().timeout());
        this.client = RestClient.builder().requestFactory(factory).baseUrl(properties.gemini().baseUrl()).build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    public boolean configured() {
        return !properties.gemini().apiKey().isBlank();
    }

    @Override
    public List<String> suggest(Prompt prompt) {
        Map<String, Object> schema = Map.of("type", "OBJECT",
                "properties", Map.of("tags", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "maxItems", prompt.max())),
                "required", List.of("tags"));
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", TagSuggester.instruction(prompt))))),
                "generationConfig", Map.of("responseMimeType", "application/json", "responseSchema", schema,
                        "maxOutputTokens", 100, "temperature", 0.2));
        String response;
        try {
            response = client.post().uri("/v1beta/models/{model}:generateContent", properties.gemini().model())
                    .header("x-goog-api-key", properties.gemini().apiKey())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String text = new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        throw new ProviderException(res.getStatusCode().value() == 429 ? limitKind(text)
                                : ProviderException.Kind.TIMEOUT_OR_SERVER);
                    })
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw new ProviderException(ProviderException.Kind.TIMEOUT_OR_SERVER);
        } catch (RestClientException e) {
            throw new ProviderException(ProviderException.Kind.TIMEOUT_OR_SERVER);
        }
        try {
            JsonNode root = jsonMapper.readTree(response);
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString("");
            return parseTags(jsonMapper, text, prompt.max());
        } catch (RuntimeException e) {
            if (e instanceof ProviderException pe) {
                throw pe;
            }
            throw new ProviderException(ProviderException.Kind.INVALID_FORMAT);
        }
    }

    /** 429 본문의 한도 이름(QuotaFailure.violations[].quotaId)으로 종류를 가른다. */
    static ProviderException.Kind limitKind(String body) {
        if (body.contains("PerDay")) {
            return ProviderException.Kind.DAILY_LIMIT;
        }
        if (body.contains("PerMinute")) {
            return ProviderException.Kind.MINUTE_LIMIT;
        }
        return ProviderException.Kind.UNKNOWN_LIMIT;
    }

    /** {@code {"tags":[…]}} 문자열 목록, 최대 {@code max}개. 아니면 형식 실패. */
    static List<String> parseTags(JsonMapper jsonMapper, String json, int max) {
        JsonNode tags = jsonMapper.readTree(json).get("tags");
        if (tags == null || !tags.isArray() || tags.size() > max) {
            throw new ProviderException(ProviderException.Kind.INVALID_FORMAT);
        }
        List<String> result = new ArrayList<>();
        for (JsonNode t : tags) {
            if (!t.isString()) {
                throw new ProviderException(ProviderException.Kind.INVALID_FORMAT);
            }
            result.add(t.asString());
        }
        return result;
    }
}

package com.team.blog.teaser.application;

import com.team.blog.tag.application.suggest.AiTagProperties;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 티저 한 줄 만들기(035): Gemini(키가 있으면) → 실패하면 자체 Ollama. 021의 연결 설정을 그대로 쓴다. 둘 다 안 되면 빈 값.
 * 응답은 글자로만 다듬어(따옴표·줄바꿈·해시태그 제거) 길이를 자른다.
 */
@Component
public class AiTeaserClient {

    static final String INSTRUCTION = """
            다음 블로그 글을 읽고 싶게 만드는 한국어 한 문장 소개를 써 줘.
            규칙: 60자 이내, 존댓말 아닌 담백한 문장, 따옴표·이모지·해시태그·마크다운 없이 문장 하나만.
            글:
            """;

    private final AiTagProperties ai;
    private final JsonMapper jsonMapper;
    private final RestClient gemini;
    private final RestClient ollama;

    public AiTeaserClient(AiTagProperties ai, JsonMapper jsonMapper) {
        this.ai = ai;
        this.jsonMapper = jsonMapper;
        this.gemini = client(ai.gemini().baseUrl(), ai.gemini().timeout());
        this.ollama = client(ai.ollama().baseUrl(), ai.ollama().timeout());
    }

    private static RestClient client(String base, java.time.Duration timeout) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        f.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(f).baseUrl(base).build();
    }

    public Optional<String> write(String text, int maxLength) {
        if (!ai.gemini().apiKey().isBlank()) {
            Optional<String> g = call(() -> {
                Map<String, Object> body = Map.of(
                        "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", INSTRUCTION + text)))),
                        "generationConfig", Map.of("maxOutputTokens", 120, "temperature", 0.7));
                String res = gemini.post().uri("/v1beta/models/{model}:generateContent", ai.gemini().model())
                        .header("x-goog-api-key", ai.gemini().apiKey()).contentType(MediaType.APPLICATION_JSON).body(body)
                        .retrieve().onStatus(HttpStatusCode::isError, (req, r) -> {
                            throw new RestClientException("gemini " + r.getStatusCode());
                        }).body(String.class);
                JsonNode root = jsonMapper.readTree(res);
                return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString("");
            }, maxLength);
            if (g.isPresent()) {
                return g;
            }
        }
        return call(() -> {
            Map<String, Object> body = Map.of("model", ai.ollama().model(), "prompt", INSTRUCTION + text, "stream", false,
                    "options", Map.of("num_predict", 120, "temperature", 0.7));
            String res = ollama.post().uri("/api/generate").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().onStatus(HttpStatusCode::isError, (req, r) -> {
                        throw new RestClientException("ollama " + r.getStatusCode());
                    }).body(String.class);
            return jsonMapper.readTree(res).path("response").asString("");
        }, maxLength);
    }

    private Optional<String> call(java.util.concurrent.Callable<String> request, int maxLength) {
        try {
            return Optional.of(clean(request.call(), maxLength)).filter(s -> !s.isEmpty());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** AI 응답 다듬기: 첫 줄만, 따옴표·마크다운·해시태그 제거, 길이 자르기. */
    static String clean(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String line = raw.strip().split("\\R", 2)[0];
        return oneLine(line.replaceAll("#\\S+", "").replaceAll("[\"'“”‘’`*_>]", ""), maxLength);
    }

    /** 글쓴이가 쓴 값: 한 줄로, 길이만 자른다(화면에는 글자로만 나간다). */
    static String oneLine(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String line = raw.replaceAll("\\s+", " ").strip();
        if (line.codePointCount(0, line.length()) > maxLength) {
            line = line.substring(0, line.offsetByCodePoints(0, maxLength)).strip();
        }
        return line;
    }
}

package com.team.blog.tag.infra;

import com.team.blog.tag.application.suggest.AiTagProperties;
import com.team.blog.tag.application.suggest.TagSuggester;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 자체 서버의 Ollama(34 §2·§8). {@code format}에 JSON 스키마, 외부로 보내지 않는다. 시간 제한 30초. */
@Component
public class OllamaTagSuggester implements TagSuggester {

    private final JsonMapper jsonMapper;
    private final AiTagProperties properties;
    private final RestClient client;

    public OllamaTagSuggester(AiTagProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.ollama().timeout()).build());
        factory.setReadTimeout(properties.ollama().timeout());
        this.client = RestClient.builder().requestFactory(factory).baseUrl(properties.ollama().baseUrl()).build();
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public List<String> suggest(Prompt prompt) {
        Map<String, Object> schema = Map.of("type", "object",
                "properties", Map.of("tags", Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", prompt.max())),
                "required", List.of("tags"));
        Map<String, Object> body = Map.of("model", properties.ollama().model(), "prompt", TagSuggester.instruction(prompt),
                "stream", false, "format", schema, "options", Map.of("num_predict", 100, "temperature", 0.2));
        String response;
        try {
            response = client.post().uri("/api/generate").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ProviderException(ProviderException.Kind.TIMEOUT_OR_SERVER);
                    })
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ProviderException(ProviderException.Kind.TIMEOUT_OR_SERVER);
        }
        try {
            JsonNode root = jsonMapper.readTree(response);
            return GeminiTagSuggester.parseTags(jsonMapper, root.path("response").asString(""), prompt.max());
        } catch (ProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ProviderException(ProviderException.Kind.INVALID_FORMAT);
        }
    }
}

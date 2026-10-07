package com.team.blog.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mailpit HTTP API 도우미(001 T107): 받은 메일 목록·본문 조회, 링크 토큰 추출, 전체 삭제.
 * 메일은 커밋 후 비동기 없이 보내지만 SMTP 수신 반영에 약간의 지연이 있을 수 있어 조회는 잠깐 기다린다.
 */
public class MailpitClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public MailpitClient(String base) {
        this.base = base;
    }

    /** 받은 메일 한 통. */
    public record Mail(String id, List<String> to, String subject, String text, String html) {

        /** 본문(텍스트+HTML)에서 {@code path?token=…}의 토큰. */
        public Optional<String> token(String path) {
            Matcher m = Pattern.compile(Pattern.quote(path) + "\\?token=([A-Za-z0-9_-]+)").matcher(text + "\n" + html);
            return m.find() ? Optional.of(m.group(1)) : Optional.empty();
        }

        public String body() {
            return text + "\n" + html;
        }
    }

    /** 수신 주소로 온 메일(오래된 것부터). */
    public List<Mail> messagesTo(String address) {
        List<Mail> result = new ArrayList<>();
        for (Mail mail : all()) {
            if (mail.to().stream().anyMatch(a -> a.equalsIgnoreCase(address))) {
                result.add(mail);
            }
        }
        return result;
    }

    /** {@code count}통이 올 때까지 최대 5초 기다린 뒤 그 주소의 메일 목록을 돌려준다. */
    public List<Mail> awaitMessagesTo(String address, int count) {
        long deadline = System.currentTimeMillis() + 5_000;
        List<Mail> mails = messagesTo(address);
        while (mails.size() < count && System.currentTimeMillis() < deadline) {
            sleep(100);
            mails = messagesTo(address);
        }
        return mails;
    }

    /** 메일이 오지 않음을 확인할 때: 잠깐 기다린 뒤 개수. */
    public int countAfterQuietPeriod(String address) {
        sleep(500);
        return messagesTo(address).size();
    }

    public List<Mail> all() {
        JsonNode list = get("/api/v1/messages?limit=500");
        List<Mail> result = new ArrayList<>();
        for (JsonNode summary : list.path("messages")) {
            String id = summary.path("ID").asString();
            JsonNode full = get("/api/v1/message/" + id);
            List<String> to = new ArrayList<>();
            for (JsonNode addr : full.path("To")) {
                to.add(addr.path("Address").asString());
            }
            result.add(new Mail(id, to, full.path("Subject").asString(), full.path("Text").asString(),
                    full.path("HTML").asString()));
        }
        // API는 최신 메일이 먼저 온다 → 오래된 것부터로 뒤집는다
        java.util.Collections.reverse(result);
        return result;
    }

    public void deleteAll() {
        send(HttpRequest.newBuilder(URI.create(base + "/api/v1/messages")).DELETE().build());
    }

    private JsonNode get(String path) {
        String body = send(HttpRequest.newBuilder(URI.create(base + path)).GET().build());
        return JSON.readTree(body);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Mailpit API " + request.uri() + " -> " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.team.blog.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 브라우저 대신 사전 서명 주소에 직접 PUT/GET 하는 테스트 도우미(003 T209). 앱 서버를 거치지 않는다.
 */
public final class StorageTestClient {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private StorageTestClient() {
    }

    /** @return HTTP 상태 코드 */
    public static int put(String url, Map<String, String> headers, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(builder::header);
        return send(builder.build());
    }

    /** 익명 GET 상태 코드(공개 주소 확인용). */
    public static int get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build());
    }

    private static int send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

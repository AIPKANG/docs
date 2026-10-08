package com.team.blog.media.application;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사진 저장소 접속값(research R-5). 운영(NHN MinIO)과 로컬(MinIO 커뮤니티 포크)은 이 값만 다르다(04 §6-1).
 * 키는 환경 변수로만 넣는다(헌법 IV) — {@link #toString()}에 비밀값을 넣지 않는다.
 *
 * @param endpoint      S3 API 주소(path-style)
 * @param publicBaseUrl 사진을 내려줄 공개 주소(저장소 또는 CDN). 비면 endpoint
 * @param bucket        버킷 이름
 * @param region        SigV4 서명 리전
 * @param accessKey     접근 키(환경 변수)
 * @param secretKey     비밀 키(환경 변수)
 * @param presignEndpoint 업로드 서명에 쓸 공개 S3 주소(비면 endpoint)
 * @param presignTtl    업로드 주소 유효 시간(04 §4-1: 5분)
 * @param createBucket  시작할 때 버킷·익명 읽기 정책을 만들지(개발·테스트만)
 * @param type          {@code s3}(기본) 또는 {@code local}(저장소를 띄울 수 없는 환경, 008 research R-6)
 * @param localDir      {@code local}일 때 파일을 둘 디렉터리
 * @param localSecret   {@code local}일 때 업로드 주소 서명 키(환경 변수)
 */
@ConfigurationProperties("blog.storage")
public record StorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        String publicBaseUrl,
        @DefaultValue("blog-images") String bucket,
        @DefaultValue("us-east-1") String region,
        String accessKey,
        String secretKey,
        @DefaultValue("5m") Duration presignTtl,
        @DefaultValue("false") boolean createBucket,
        @DefaultValue("s3") String type,
        @DefaultValue("./build/local-images") String localDir,
        String localSecret,
        String presignEndpoint) {

    /** 브라우저가 직접 올릴 때 쓰는 서명 주소. 앱이 저장소를 내부 주소로 부르는 배포(컨테이너 등)에서만 따로 둔다. 비면 endpoint. */
    public String presignEndpointOrDefault() {
        return presignEndpoint == null || presignEndpoint.isBlank() ? endpoint : presignEndpoint;
    }

    public boolean isLocal() {
        return "local".equals(type);
    }

    /** 공개 주소 기준(끝의 {@code /} 제거). */
    public String effectivePublicBaseUrl() {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank() ? endpoint : publicBaseUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** CSP용 출처({@code scheme://host[:port]}). */
    public static String origin(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri = URI.create(url.strip());
        if (uri.getScheme() == null || uri.getHost() == null) {
            return null;
        }
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    @Override
    public String toString() {
        return "StorageProperties[endpoint=" + endpoint + ", publicBaseUrl=" + publicBaseUrl + ", bucket=" + bucket
                + ", region=" + region + ", presignTtl=" + presignTtl + ", createBucket=" + createBucket + "]";
    }
}

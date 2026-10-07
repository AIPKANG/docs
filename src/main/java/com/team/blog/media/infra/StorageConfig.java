package com.team.blog.media.infra;

import com.team.blog.media.application.StorageProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 호환 저장소 클라이언트(04 §4-1: {@code endpointOverride} + {@code forcePathStyle(true)}, research R-5).
 * 사전 서명은 {@link S3Presigner}(항상 SigV4). 요청 체크섬은 필요할 때만 계산해 사전 서명 PUT에 체크섬 헤더가 서명되지 않게 한다.
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .forcePathStyle(true)
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    static AwsCredentialsProvider credentials(StorageProperties properties) {
        if (properties.accessKey() == null || properties.accessKey().isBlank()
                || properties.secretKey() == null || properties.secretKey().isBlank()) {
            // 키가 없으면(설정 누락) 익명으로 만들어 두고, 운영은 RequiredSecretsCheck가 기동을 막는다
            return AnonymousCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}

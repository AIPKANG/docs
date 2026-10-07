package com.team.blog.media.infra;

import com.team.blog.media.application.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 개발·테스트 전용({@code blog.storage.create-bucket=true}): 시작할 때 버킷이 없으면 만들고, 익명은 {@code images/*}의 파일 하나
 * 읽기({@code s3:GetObject})만 허용하는 정책을 건다(04 결정 2, 23 §2-2 — 목록·쓰기는 허용하지 않음).
 * 운영(NHN MinIO)은 버킷·정책·앱 전용 키를 미리 만들어 두고 이 작업을 끈다. 앱 전용 키 발급은 008 범위다.
 */
@Component
@ConditionalOnProperty(name = "blog.storage.create-bucket", havingValue = "true")
public class StorageBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StorageBucketInitializer.class);

    private final S3Client s3;
    private final StorageProperties properties;

    public StorageBucketInitializer(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.bucket();
        if (!exists(bucket)) {
            s3.createBucket(request -> request.bucket(bucket));
            log.info("저장소 버킷 생성: {}", bucket);
        }
        s3.putBucketPolicy(request -> request.bucket(bucket).policy(anonymousReadPolicy(bucket)));
    }

    private boolean exists(String bucket) {
        try {
            s3.headBucket(request -> request.bucket(bucket));
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    static String anonymousReadPolicy(String bucket) {
        return """
                {"Version":"2012-10-17","Statement":[{"Sid":"PublicReadImages","Effect":"Allow",\
                "Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/images/*"]}]}"""
                .formatted(bucket);
    }
}

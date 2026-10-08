package com.team.blog.media.infra;

import com.team.blog.media.application.ImageStorage;
import com.team.blog.media.application.StorageProperties;
import com.team.blog.media.application.StoredObject;
import com.team.blog.media.application.UploadTarget;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/** MinIO·S3 호환 저장소 구현(04 §4-1, §6-1). 키·서명은 로그에 남기지 않는다. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "blog.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3ImageStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public UploadTarget prepareUpload(String key, String contentType, long size) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
                .signatureDuration(properties.presignTtl())
                .putObjectRequest(put));
        Instant expiresAt = presigned.expiration();
        return new UploadTarget(presigned.url().toString(), "PUT",
                Map.of("Content-Type", contentType, "Cache-Control", CACHE_CONTROL), expiresAt);
    }

    @Override
    public Optional<StoredObject> inspect(String key) {
        HeadObjectResponse head;
        try {
            head = s3.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
        long size = head.contentLength() == null ? 0 : head.contentLength();
        byte[] bytes = new byte[0];
        if (size > 0) {
            long last = Math.min(size, StoredObject.HEAD_BYTES) - 1;
            ResponseBytes<GetObjectResponse> body = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket()).key(key).range("bytes=0-" + last).build());
            bytes = body.asByteArray();
        }
        return Optional.of(new StoredObject(size, head.contentType(), bytes));
    }

    @Override
    public Optional<byte[]> read(String key, long maxBytes) {
        try {
            ResponseBytes<GetObjectResponse> body = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket()).key(key).range("bytes=0-" + (maxBytes - 1)).build());
            return Optional.of(body.asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 416) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public String publicUrl(String key) {
        return properties.effectivePublicBaseUrl() + "/" + properties.bucket() + "/" + key;
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }
}

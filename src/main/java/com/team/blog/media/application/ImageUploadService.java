package com.team.blog.media.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.media.domain.Image;
import com.team.blog.media.domain.ImageFormat;
import com.team.blog.media.domain.ImageHeader;
import com.team.blog.media.domain.ImageHeaderInspector;
import com.team.blog.media.domain.ImagePurpose;
import com.team.blog.media.infra.ImageRepository;
import com.team.blog.shared.error.ImageInvalidException;
import com.team.blog.shared.error.ImageInvalidException.Detail;
import com.team.blog.shared.error.ImagePurposeNotSupportedException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 프로필 이미지 업로드 승인·완료(11 §4-1, 04 §4-1, 003 research R-7). 008이 글 사진(원본·썸네일·용량)을 더한다.
 *
 * <p>권한: {@link AccountGuard#requireWritable} — 비회원 401, 탈퇴 유예·이메일 인증 전 403(42 §10).
 * 저장소 호출(inspect·delete)은 DB 트랜잭션 밖에서 한다(헌법 V).
 */
@Service
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

    private final AccountGuard accountGuard;
    private final ImageRepository imageRepository;
    private final ImageStorage storage;
    private final RedisRateLimiter rateLimiter;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final ImageProperties properties;
    private final Clock clock;

    public ImageUploadService(AccountGuard accountGuard, ImageRepository imageRepository, ImageStorage storage,
                              RedisRateLimiter rateLimiter, StringRedisTemplate redis,
                              TransactionTemplate transactionTemplate, ImageProperties properties, Clock clock) {
        this.accountGuard = accountGuard;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.rateLimiter = rateLimiter;
        this.redis = redis;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 승인: 용도(PROFILE만) → 형식 4종 → 크기 1..1MiB → 1분 20장 → TEMP 행 → 5분 유효 PUT 주소.
     *
     * @throws ImagePurposeNotSupportedException 글 사진 등(008 전)
     * @throws ImageInvalidException             {@code TYPE}·{@code SIZE}
     * @throws RateLimitedException              1분 한도 초과
     */
    public PresignResult presign(Optional<CurrentUser> currentUser, PresignCommand command) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        if (!ImagePurpose.PROFILE.name().equals(command.purpose())) {
            throw new ImagePurposeNotSupportedException();
        }
        ImageFormat format = ImageFormat.fromContentType(command.contentType())
                .orElseThrow(() -> new ImageInvalidException(Detail.TYPE));
        long size = command.size() == null ? 0 : command.size();
        if (size <= 0 || size > properties.profile().maxBytes()) {
            throw new ImageInvalidException(Detail.SIZE);
        }
        RedisRateLimiter.Result limit = rateLimiter.tryAcquire(
                RedisRateLimiter.key("img:upload", String.valueOf(user.memberId())),
                properties.uploadPerMinute(), Duration.ofMinutes(1));
        if (!limit.allowed()) {
            throw new RateLimitedException(limit.retryAfterSeconds());
        }
        Instant now = clock.instant();
        String key = storageKey(now, format);
        Image saved = transactionTemplate.execute(status -> imageRepository.save(Image.profileUpload(
                user.memberId(), key, originalName(command.originalName(), format), format, (int) size, now)));
        UploadTarget target = storage.prepareUpload(key, format.contentType(), size);
        return new PresignResult(saved.getId(), target.url(), target.method(), target.headers(), target.expiresAt());
    }

    /**
     * 완료: 본인 사진이 아니면 404(존재 비노출) → 이미 완료면 같은 결과 → 저장소 확인 → 규격 검사.
     * 실패하면 저장소 파일과 행을 지우고 {@link ImageInvalidException}.
     */
    public CompleteResult complete(Optional<CurrentUser> currentUser, long imageId) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        Image image = imageRepository.findByIdAndUploaderId(imageId, user.memberId()).orElseThrow(NotFoundException::new);
        if (image.isCompleted()) {
            return result(image);
        }
        Optional<StoredObject> stored = storage.inspect(image.getStorageKey());
        Detail failure = validate(image, stored);
        if (failure != null) {
            discard(image);
            throw new ImageInvalidException(failure);
        }
        ImageHeader header = ImageHeaderInspector.inspect(stored.get().head()).orElseThrow();
        Image completed = transactionTemplate.execute(status -> {
            Image locked = imageRepository.findByIdForUpdate(imageId).orElseThrow(NotFoundException::new);
            if (!locked.isCompleted()) {
                locked.complete(header.width(), header.height(), (int) stored.get().size());
            }
            return locked;
        });
        return result(completed);
    }

    private Detail validate(Image image, Optional<StoredObject> stored) {
        if (stored.isEmpty()) {
            return Detail.MISSING;
        }
        long actual = stored.get().size();
        if (actual <= 0 || actual > image.getSizeBytes() || actual > properties.profile().maxBytes()) {
            return Detail.SIZE;
        }
        Optional<ImageHeader> header = ImageHeaderInspector.inspect(stored.get().head());
        if (header.isEmpty() || !header.get().format().contentType().equals(image.getContentType())) {
            return Detail.CONTENT_MISMATCH;
        }
        int size = properties.profile().size();
        if (header.get().width() != size || header.get().height() != size) {
            return Detail.DIMENSION;
        }
        if (header.get().hasMetadata()) {
            return Detail.METADATA;
        }
        return null;
    }

    /** 거부된 업로드: 저장소 파일 삭제(실패하면 정리 작업의 재시도 집합으로) 후 행 삭제. */
    private void discard(Image image) {
        try {
            storage.delete(image.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("거부된 사진 파일 삭제 실패, 정리 작업에서 재시도: imageId={} cause={}", image.getId(),
                    e.getClass().getSimpleName());
            redis.opsForSet().add(ImageCleanupService.ORPHAN_KEYS, image.getStorageKey());
        }
        transactionTemplate.executeWithoutResult(status -> imageRepository.deleteById(image.getId()));
    }

    private CompleteResult result(Image image) {
        return new CompleteResult(image.getId(), storage.publicUrl(image.getStorageKey()), image.getWidth(), image.getHeight());
    }

    /** {@code images/{yyyy}/{MM}/{uuid}.{ext}} — 원래 파일 이름은 쓰지 않는다(04 §4-2). */
    static String storageKey(Instant now, ImageFormat format) {
        ZonedDateTime time = now.atZone(ZoneOffset.UTC);
        return String.format(Locale.ROOT, "images/%04d/%02d/%s.%s", time.getYear(), time.getMonthValue(),
                UUID.randomUUID(), format.extension());
    }

    /** {@code original_name}: 기록으로만 보관(경로·화면에 쓰지 않음). 제어 문자 제거, 255자 이하. */
    static String originalName(String raw, ImageFormat format) {
        String cleaned = raw == null ? "" : raw.codePoints()
                .filter(cp -> !Character.isISOControl(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().strip();
        if (cleaned.isEmpty()) {
            return "profile." + format.extension();
        }
        if (cleaned.length() > 255) {
            int end = Character.isHighSurrogate(cleaned.charAt(254)) ? 254 : 255;
            cleaned = cleaned.substring(0, end);
        }
        return cleaned;
    }
}

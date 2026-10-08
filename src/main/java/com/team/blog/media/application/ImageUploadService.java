package com.team.blog.media.application;

import com.team.blog.account.infra.RedisRateLimiter;
import com.team.blog.media.domain.GifFrameCounter;
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
import java.util.OptionalInt;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사진 업로드 승인·완료. 프로필(11 §4-1, 003 research R-7)과 글 사진(04 §4-1, 23, 008 research R-1·R-2: 원본·썸네일·한도).
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
    private final UploadQuota quota;

    public ImageUploadService(AccountGuard accountGuard, ImageRepository imageRepository, ImageStorage storage,
                              RedisRateLimiter rateLimiter, StringRedisTemplate redis,
                              TransactionTemplate transactionTemplate, ImageProperties properties, Clock clock,
                              UploadQuota quota) {
        this.accountGuard = accountGuard;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.rateLimiter = rateLimiter;
        this.redis = redis;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
        this.quota = quota;
    }

    /**
     * 승인. 프로필(003): 형식 4종 → 1..1MiB → 1분 20장 → TEMP 행 → 5분 유효 PUT 주소.
     * 글 사진(008 research R-1): 형식 → 원본 1..10MB·썸네일 1..1MB → 1분 20장 → 하루 200장(실패해도 셈) →
     * 회원별 잠금 아래 1GB 확인 → TEMP 행 → 원본·썸네일 PUT 주소.
     *
     * @throws ImagePurposeNotSupportedException 모르는 용도
     * @throws ImageInvalidException             {@code TYPE}·{@code SIZE}
     * @throws RateLimitedException              1분 한도 초과
     */
    public PresignResult presign(Optional<CurrentUser> currentUser, PresignCommand command) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        ImagePurpose purpose;
        try {
            purpose = command.purpose() == null ? null : ImagePurpose.valueOf(command.purpose());
        } catch (IllegalArgumentException e) {
            purpose = null;
        }
        if (purpose == null) {
            throw new ImagePurposeNotSupportedException();
        }
        ImageFormat format = ImageFormat.fromContentType(command.contentType())
                .orElseThrow(() -> new ImageInvalidException(Detail.TYPE));
        long size = command.size() == null ? 0 : command.size();
        long maxBytes = purpose == ImagePurpose.PROFILE ? properties.profile().maxBytes() : properties.post().maxBytes();
        if (size <= 0 || size > maxBytes) {
            throw new ImageInvalidException(Detail.SIZE);
        }
        Long thumbSize = purpose == ImagePurpose.POST ? command.thumbSize() : null;
        if (thumbSize != null && (thumbSize <= 0 || thumbSize > properties.post().thumbMaxBytes())) {
            throw new ImageInvalidException(Detail.THUMBNAIL);
        }
        RedisRateLimiter.Result limit = rateLimiter.tryAcquire(
                RedisRateLimiter.key("img:upload", String.valueOf(user.memberId())),
                properties.uploadPerMinute(), Duration.ofMinutes(1));
        if (!limit.allowed()) {
            throw new RateLimitedException(limit.retryAfterSeconds());
        }
        Instant now = clock.instant();
        String key = storageKey(now, format);
        if (purpose == ImagePurpose.PROFILE) {
            Image saved = transactionTemplate.execute(status -> imageRepository.save(Image.profileUpload(
                    user.memberId(), key, originalName(command.originalName(), format, "profile"), format, (int) size, now)));
            UploadTarget target = storage.prepareUpload(key, format.contentType(), size);
            return new PresignResult(saved.getId(), target.url(), target.method(), target.headers(), target.expiresAt());
        }
        quota.countDailyUpload(user.memberId(), now);
        String thumbKey = thumbSize == null ? null : thumbKey(key);
        long total = size + (thumbSize == null ? 0 : thumbSize);
        Image saved = transactionTemplate.execute(status -> {
            quota.lockAndCheck(user.memberId(), total);
            return imageRepository.save(Image.postUpload(user.memberId(), key, thumbKey,
                    originalName(command.originalName(), format, "image"), format, (int) size,
                    thumbSize == null ? null : thumbSize.intValue(), now));
        });
        UploadTarget target = storage.prepareUpload(key, format.contentType(), size);
        UploadTarget thumb = thumbKey == null ? null
                : storage.prepareUpload(thumbKey, ImageFormat.WEBP.contentType(), thumbSize);
        return new PresignResult(saved.getId(), target.url(), target.method(), target.headers(), target.expiresAt(),
                thumb == null ? null : thumb.url(), thumb == null ? null : thumb.headers());
    }

    /**
     * 완료: 본인 사진이 아니면 404(존재 비노출) → 이미 완료면 같은 결과 → 저장소 확인 → 규격 검사.
     * 실패하면 저장소 파일(원본·썸네일)과 행을 지우고 {@link ImageInvalidException}.
     */
    public CompleteResult complete(Optional<CurrentUser> currentUser, long imageId) {
        CurrentUser user = accountGuard.requireWritable(currentUser);
        Image image = imageRepository.findByIdAndUploaderId(imageId, user.memberId()).orElseThrow(NotFoundException::new);
        if (image.isCompleted()) {
            return result(image);
        }
        Optional<StoredObject> stored = storage.inspect(image.getStorageKey());
        Optional<StoredObject> storedThumb = image.getThumbStorageKey() == null ? Optional.empty()
                : storage.inspect(image.getThumbStorageKey());
        Detail failure = image.getPurpose() == ImagePurpose.PROFILE ? validateProfile(image, stored)
                : validatePost(image, stored, storedThumb);
        if (failure != null) {
            discard(image);
            throw new ImageInvalidException(failure);
        }
        ImageHeader header = ImageHeaderInspector.inspect(stored.get().head()).orElseThrow();
        Integer thumbSize = storedThumb.map(t -> (int) t.size()).orElse(null);
        Image completed = transactionTemplate.execute(status -> {
            Image locked = imageRepository.findByIdForUpdate(imageId).orElseThrow(NotFoundException::new);
            if (!locked.isCompleted()) {
                if (locked.getPurpose() == ImagePurpose.POST) {
                    locked.complete(header.width(), header.height(), (int) stored.get().size(), thumbSize);
                } else {
                    locked.complete(header.width(), header.height(), (int) stored.get().size());
                }
            }
            return locked;
        });
        return result(completed);
    }

    private Detail validateProfile(Image image, Optional<StoredObject> stored) {
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

    /** 008 research R-2: 원본(크기·형식 위장·해상도·EXIF·GIF 프레임)과 썸네일(WebP·가로 640·1MB) 재검사. */
    private Detail validatePost(Image image, Optional<StoredObject> stored, Optional<StoredObject> thumb) {
        ImageProperties.Post rules = properties.post();
        if (stored.isEmpty()) {
            return Detail.MISSING;
        }
        long actual = stored.get().size();
        if (actual <= 0 || actual > image.getSizeBytes() || actual > rules.maxBytes()) {
            return Detail.SIZE;
        }
        Optional<ImageHeader> header = ImageHeaderInspector.inspect(stored.get().head());
        if (header.isEmpty() || !header.get().format().contentType().equals(image.getContentType())) {
            return Detail.CONTENT_MISMATCH;
        }
        boolean gif = header.get().format() == ImageFormat.GIF;
        int maxDimension = gif ? rules.gifMaxDimension() : rules.maxDimension();
        if (header.get().width() > maxDimension || header.get().height() > maxDimension) {
            return Detail.DIMENSION;
        }
        if (header.get().hasMetadata()) {
            return Detail.METADATA;
        }
        if (gif) {
            Optional<byte[]> whole = storage.read(image.getStorageKey(), rules.maxBytes());
            OptionalInt frames = whole.map(bytes -> GifFrameCounter.count(bytes, rules.gifMaxFrames()))
                    .orElse(OptionalInt.empty());
            if (frames.isEmpty() || frames.getAsInt() > rules.gifMaxFrames()) {
                return Detail.FRAMES;
            }
        }
        if (image.getThumbStorageKey() != null) {
            if (thumb.isEmpty() || thumb.get().size() <= 0 || thumb.get().size() > rules.thumbMaxBytes()
                    || (image.getThumbSizeBytes() != null && thumb.get().size() > image.getThumbSizeBytes())) {
                return Detail.THUMBNAIL;
            }
            Optional<ImageHeader> thumbHeader = ImageHeaderInspector.inspect(thumb.get().head());
            if (thumbHeader.isEmpty() || thumbHeader.get().format() != ImageFormat.WEBP
                    || thumbHeader.get().width() > rules.thumbMaxWidth() || thumbHeader.get().hasMetadata()) {
                return Detail.THUMBNAIL;
            }
        }
        return null;
    }

    /** 거부된 업로드: 저장소 파일 삭제(실패하면 정리 작업의 재시도 집합으로) 후 행 삭제. */
    private void discard(Image image) {
        for (String key : image.getThumbStorageKey() == null ? java.util.List.of(image.getStorageKey())
                : java.util.List.of(image.getStorageKey(), image.getThumbStorageKey())) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("거부된 사진 파일 삭제 실패, 정리 작업에서 재시도: imageId={} cause={}", image.getId(),
                        e.getClass().getSimpleName());
                redis.opsForSet().add(ImageCleanupService.ORPHAN_KEYS, key);
            }
        }
        transactionTemplate.executeWithoutResult(status -> imageRepository.deleteById(image.getId()));
    }

    private CompleteResult result(Image image) {
        String thumbUrl = image.getThumbStorageKey() == null ? null : storage.publicUrl(image.getThumbStorageKey());
        return new CompleteResult(image.getId(), storage.publicUrl(image.getStorageKey()), image.getWidth(),
                image.getHeight(), thumbUrl);
    }

    /** 썸네일 키: 원본 키의 확장자 앞에 {@code _thumb}, 확장자 {@code webp}(10 §6). */
    static String thumbKey(String key) {
        int dot = key.lastIndexOf('.');
        return (dot < 0 ? key : key.substring(0, dot)) + "_thumb.webp";
    }

    /** {@code images/{yyyy}/{MM}/{uuid}.{ext}} — 원래 파일 이름은 쓰지 않는다(04 §4-2). */
    static String storageKey(Instant now, ImageFormat format) {
        ZonedDateTime time = now.atZone(ZoneOffset.UTC);
        return String.format(Locale.ROOT, "images/%04d/%02d/%s.%s", time.getYear(), time.getMonthValue(),
                UUID.randomUUID(), format.extension());
    }

    /** {@code original_name}: 기록으로만 보관(경로·화면에 쓰지 않음). 제어 문자 제거, 255자 이하. */
    static String originalName(String raw, ImageFormat format) {
        return originalName(raw, format, "profile");
    }

    static String originalName(String raw, ImageFormat format, String fallbackBase) {
        String cleaned = raw == null ? "" : raw.codePoints()
                .filter(cp -> !Character.isISOControl(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().strip();
        if (cleaned.isEmpty()) {
            return fallbackBase + "." + format.extension();
        }
        if (cleaned.length() > 255) {
            int end = Character.isHighSurrogate(cleaned.charAt(254)) ? 254 : 255;
            cleaned = cleaned.substring(0, end);
        }
        return cleaned;
    }
}

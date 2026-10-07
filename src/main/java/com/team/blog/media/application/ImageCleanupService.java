package com.team.blog.media.application;

import com.team.blog.media.infra.ImageCleanupStore;
import com.team.blog.media.infra.ImageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 버려진 사진 정리(04 §4-4, 11 §4-4, 003 research R-9): TEMP로 24시간, 연결이 끊긴 지 7일 지난 사진을 지운다.
 *
 * <p>순서: (1) 지난번 저장소 삭제에 실패한 키 재시도 → (2) 후보 조회 → (3) 다른 모듈이 참조 중인 사진 제외
 * ({@link ImageReferenceLookup}) → (4) 행을 조건부로 지우고(건마다 짧은 트랜잭션) → (5) 커밋 후 저장소 파일 삭제.
 * 행을 먼저 지우므로 그사이 [저장]으로 연결된 사진의 파일은 지워지지 않는다. 저장소 삭제 실패 키는
 * Redis 집합 {@link #ORPHAN_KEYS}에 남겨 다음 실행에서 다시 지운다.
 */
@Service
public class ImageCleanupService {

    public static final String ORPHAN_KEYS = "img:orphan-keys";

    private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);

    /** 실행 결과(로그·테스트용). */
    public record CleanupReport(int deleted, int skippedReferenced, int orphanRetried, int storageFailures) {
    }

    private final ImageRepository imageRepository;
    private final ImageCleanupStore cleanupStore;
    private final List<ImageReferenceLookup> referenceLookups;
    private final ImageStorage storage;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final ImageProperties properties;
    private final Clock clock;

    public ImageCleanupService(ImageRepository imageRepository, ImageCleanupStore cleanupStore,
                               List<ImageReferenceLookup> referenceLookups, ImageStorage storage,
                               StringRedisTemplate redis, TransactionTemplate transactionTemplate,
                               ImageProperties properties, Clock clock) {
        this.imageRepository = imageRepository;
        this.cleanupStore = cleanupStore;
        this.referenceLookups = referenceLookups;
        this.storage = storage;
        this.redis = redis;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public CleanupReport runOnce() {
        int orphanRetried = retryOrphans();
        Instant now = clock.instant();
        Instant tempBefore = now.minus(properties.tempTtl());
        Instant detachedBefore = now.minus(properties.detachedTtl());
        List<Long> candidates = imageRepository.findCleanupCandidates(tempBefore, detachedBefore,
                PageRequest.of(0, properties.cleanup().batchSize()));
        Set<Long> referenced = new HashSet<>();
        if (!candidates.isEmpty()) {
            referenceLookups.forEach(lookup -> referenced.addAll(lookup.referencedIds(candidates)));
        }
        int deleted = 0;
        int storageFailures = 0;
        for (Long id : candidates) {
            if (referenced.contains(id)) {
                continue;
            }
            List<String> keys;
            try {
                keys = transactionTemplate.execute(status -> cleanupStore.deleteIfStillExpired(id, tempBefore, detachedBefore));
            } catch (DataIntegrityViolationException e) {
                // 그사이 다른 행이 참조하게 됨(FK RESTRICT) — 다음 실행에서 다시 본다
                log.info("정리 건너뜀(참조 중): imageId={}", id);
                continue;
            }
            if (keys == null || keys.isEmpty()) {
                continue;
            }
            deleted++;
            for (String key : keys) {
                if (!deleteFromStorage(key)) {
                    storageFailures++;
                }
            }
        }
        CleanupReport report = new CleanupReport(deleted, referenced.size(), orphanRetried, storageFailures);
        log.info("사진 정리: {}", report);
        return report;
    }

    private int retryOrphans() {
        Set<String> keys = redis.opsForSet().members(ORPHAN_KEYS);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        int retried = 0;
        for (String key : keys) {
            try {
                storage.delete(key);
                redis.opsForSet().remove(ORPHAN_KEYS, key);
                retried++;
            } catch (RuntimeException e) {
                log.warn("저장소 삭제 재시도 실패: cause={}", e.getClass().getSimpleName());
            }
        }
        return retried;
    }

    private boolean deleteFromStorage(String key) {
        try {
            storage.delete(key);
            return true;
        } catch (RuntimeException e) {
            log.warn("저장소 삭제 실패, 다음 정리에서 재시도: cause={}", e.getClass().getSimpleName());
            redis.opsForSet().add(ORPHAN_KEYS, key);
            return false;
        }
    }
}

package com.team.blog.media.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 정리 작업 예약 실행(04 §4-4 "매일 새벽"). 시각은 {@code blog.image.cleanup.cron}(Asia/Seoul), 테스트는 끈다. */
@Component
@ConditionalOnProperty(name = "blog.image.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ImageCleanupJob {

    private final ImageCleanupService cleanupService;

    public ImageCleanupJob(ImageCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${blog.image.cleanup.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        cleanupService.runOnce();
    }
}

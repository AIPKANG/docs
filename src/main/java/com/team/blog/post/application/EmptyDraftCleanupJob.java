package com.team.blog.post.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 새벽 빈 임시글 정리(04 §2-5). 시각은 {@code blog.post.empty-draft-cleanup.cron}(Asia/Seoul), 테스트는 끈다. */
@Component
@ConditionalOnProperty(name = "blog.post.empty-draft-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class EmptyDraftCleanupJob {

    static final String LOCK = "post:empty-draft-cleanup-lock";

    private final EmptyDraftCleanupService cleanupService;
    private final JobLock jobLock;
    private final PostProperties properties;

    public EmptyDraftCleanupJob(EmptyDraftCleanupService cleanupService, JobLock jobLock, PostProperties properties) {
        this.cleanupService = cleanupService;
        this.jobLock = jobLock;
        this.properties = properties;
    }

    @Scheduled(cron = "${blog.post.empty-draft-cleanup.cron:0 40 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        jobLock.runExclusively(LOCK, properties.emptyDraftCleanup().lockTtl(), cleanupService::runOnce);
    }
}

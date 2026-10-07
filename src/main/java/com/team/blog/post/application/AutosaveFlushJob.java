package com.team.blog.post.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 1분마다 영구 반영(FR-006). 서버가 여러 대여도 실행 잠금으로 한 번만 돈다. 테스트는 끄고 직접 부른다. */
@Component
@ConditionalOnProperty(name = "blog.post.autosave.flush-enabled", havingValue = "true", matchIfMissing = true)
public class AutosaveFlushJob {

    static final String LOCK = "autosave:flush-lock";

    private final AutosaveFlusher flusher;
    private final JobLock jobLock;
    private final PostProperties properties;

    public AutosaveFlushJob(AutosaveFlusher flusher, JobLock jobLock, PostProperties properties) {
        this.flusher = flusher;
        this.jobLock = jobLock;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${blog.post.autosave.flush-interval:1m}", initialDelayString = "${blog.post.autosave.flush-interval:1m}")
    public void run() {
        jobLock.runExclusively(LOCK, properties.autosave().flushLockTtl(),
                () -> flusher.flushBatch(properties.autosave().flushBatchSize()));
    }
}

package com.team.blog.post.application;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 휴지통 30일 자동 정리(011 FR-028). 서버 한 대만(실행 잠금). 테스트는 끈다. */
@Component
@ConditionalOnProperty(name = "blog.post.trash.purge-enabled", havingValue = "true", matchIfMissing = true)
public class TrashPurgeJob {

    static final String LOCK = "post:trash-purge-lock";

    private final PostTrashService trashService;
    private final JobLock jobLock;

    public TrashPurgeJob(PostTrashService trashService, JobLock jobLock) {
        this.trashService = trashService;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${blog.post.trash.purge-cron:0 20 5 * * *}", zone = "Asia/Seoul")
    public void run() {
        jobLock.runExclusively(LOCK, Duration.ofMinutes(30), () -> {
            while (trashService.purgeExpired() > 0) {
                // 남은 글이 없을 때까지 100개씩
            }
        });
    }
}

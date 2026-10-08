package com.team.blog.notification.application;

import com.team.blog.post.application.JobLock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 새벽 알림 정리(FR-032). 서버 한 대만. 테스트는 끈다. */
@Component
@ConditionalOnProperty(name = "blog.notification.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationCleanupJob {

    private final NotificationCleanup cleanup;
    private final JobLock jobLock;

    public NotificationCleanupJob(NotificationCleanup cleanup, JobLock jobLock) {
        this.cleanup = cleanup;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${blog.notification.cleanup-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        jobLock.runExclusively("notification:cleanup-lock", Duration.ofMinutes(10), cleanup::purge);
    }
}

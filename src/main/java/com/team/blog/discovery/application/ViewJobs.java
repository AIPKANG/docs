package com.team.blog.discovery.application;

import com.team.blog.post.application.JobLock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 조회수 1분 반영·90일 정리(016). 서버 한 대만. 테스트는 끈다. */
@Component
@ConditionalOnProperty(name = "blog.view.flush-enabled", havingValue = "true", matchIfMissing = true)
public class ViewJobs {

    private final ViewFlusher flusher;
    private final JobLock jobLock;
    private final ViewProperties properties;

    public ViewJobs(ViewFlusher flusher, JobLock jobLock, ViewProperties properties) {
        this.flusher = flusher;
        this.jobLock = jobLock;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${blog.view.flush-interval:1m}", initialDelayString = "${blog.view.flush-interval:1m}")
    public void flush() {
        jobLock.runExclusively("view:flush-lock", Duration.ofSeconds(50), flusher::flush);
    }

    @Scheduled(cron = "${blog.view.retention-cron:0 0 5 * * *}", zone = "Asia/Seoul")
    public void purge() {
        jobLock.runExclusively("view:purge-lock", Duration.ofMinutes(10), () -> flusher.purgeOldDaily(properties.dailyRetention()));
    }
}

package com.team.blog.discovery.application;

import com.team.blog.post.application.JobLock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 10분마다 트렌딩 스냅샷(019 FR-008). 여러 서버여도 한 번만. 테스트는 끈다. */
@Component
@ConditionalOnProperty(name = "blog.trending.refresh-enabled", havingValue = "true", matchIfMissing = true)
public class TrendingJob {

    private final TrendingService service;
    private final JobLock jobLock;

    public TrendingJob(TrendingService service, JobLock jobLock) {
        this.service = service;
        this.jobLock = jobLock;
    }

    @Scheduled(fixedDelayString = "${blog.trending.refresh-interval:10m}", initialDelayString = "PT5S")
    public void refresh() {
        jobLock.runExclusively("trending:refresh-lock", Duration.ofMinutes(5), service::refresh);
    }
}

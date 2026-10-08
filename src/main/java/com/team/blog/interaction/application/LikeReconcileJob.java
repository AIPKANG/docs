package com.team.blog.interaction.application;

import com.team.blog.post.application.JobLock;
import com.team.blog.post.application.PostCounters;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 좋아요 수 보정(015 FR-010). 고친 건수가 있으면 경고. 서버 한 대만(실행 잠금). */
@Component
@ConditionalOnProperty(name = "blog.like.reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class LikeReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(LikeReconcileJob.class);

    private final PostCounters postCounters;
    private final JobLock jobLock;

    public LikeReconcileJob(PostCounters postCounters, JobLock jobLock) {
        this.postCounters = postCounters;
        this.jobLock = jobLock;
    }

    @Scheduled(cron = "${blog.like.reconcile-cron:0 50 4 * * *}", zone = "Asia/Seoul")
    public void run() {
        jobLock.runExclusively("like:reconcile-lock", Duration.ofMinutes(30), this::runOnce);
    }

    public int runOnce() {
        int fixed = postCounters.reconcileLikes();
        if (fixed > 0) {
            log.warn("like_count mismatch fixed for {} posts", fixed);
        }
        return fixed;
    }
}

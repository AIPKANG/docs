package com.team.blog.moderation.application;

import com.team.blog.account.application.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;

/** 023 익명 처리 단계 80: 내 콘텐츠의 대기 신고를 "대상 없음"으로. 내가 한 신고는 남는다(익명 회원을 가리킴). */
@Component
public class ReportPurgeStep implements WithdrawalPurgeStep {

    private final ReportCleanupJob cleanupJob;

    public ReportPurgeStep(ReportCleanupJob cleanupJob) {
        this.cleanupJob = cleanupJob;
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public void purge(long memberId) {
        cleanupJob.closeForWithdrawn(memberId);
    }
}

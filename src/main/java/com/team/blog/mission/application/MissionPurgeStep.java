package com.team.blog.mission.application;

import com.team.blog.account.application.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;

/** 탈퇴 정리: 미션 참여 기록 삭제(연 미션은 남기고 연 사람은 "탈퇴한 사용자"로 보인다). */
@Component
public class MissionPurgeStep implements WithdrawalPurgeStep {

    private final MissionService missions;

    public MissionPurgeStep(MissionService missions) {
        this.missions = missions;
    }

    @Override
    public int order() {
        return 16;
    }

    @Override
    public void purge(long memberId) {
        missions.purgeWithdrawn(memberId);
    }
}

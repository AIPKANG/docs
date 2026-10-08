package com.team.blog.notification.application;

import com.team.blog.account.application.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;

/** 023 익명 처리 단계 70(글·댓글 단계 다음): 받은 알림 삭제, 남의 묶음에서 빼고 다시 계산, 일으킨 하나짜리 알림 삭제. */
@Component
public class NotificationPurgeStep implements WithdrawalPurgeStep {

    private final NotificationCleanup cleanup;

    public NotificationPurgeStep(NotificationCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public void purge(long memberId) {
        cleanup.purgeWithdrawn(memberId);
    }
}

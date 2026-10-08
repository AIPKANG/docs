package com.team.blog.media.application;

import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 내 사진 저장 공간 사용량(008 FR-027, 23 §3-1). */
@Service
public class StorageUsageQuery {

    public record Usage(long usedBytes, long quotaBytes, int todayCount, int dailyLimit) {

        /** 90% 이상 썼는지(에디터 안내). */
        public boolean nearlyFull() {
            return quotaBytes > 0 && usedBytes * 10 >= quotaBytes * 9;
        }
    }

    private final AccountGuard accountGuard;
    private final UploadQuota quota;
    private final ImageProperties properties;
    private final Clock clock;

    public StorageUsageQuery(AccountGuard accountGuard, UploadQuota quota, ImageProperties properties, Clock clock) {
        this.accountGuard = accountGuard;
        this.quota = quota;
        this.properties = properties;
        this.clock = clock;
    }

    public Usage usage(Optional<CurrentUser> currentUser) {
        CurrentUser user = accountGuard.requireLoggedIn(currentUser);
        return new Usage(quota.usedBytes(user.memberId()), properties.quotaBytes(),
                quota.todayCount(user.memberId(), clock.instant()), properties.dailyLimit());
    }
}

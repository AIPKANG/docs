package com.team.blog.friend.application;

import com.team.blog.account.application.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;

/** 탈퇴 정리: 친구 관계·요청 삭제(13 §3-3 "친구 관계 삭제", 팔로우 정리 60 앞). */
@Component
public class FriendPurgeStep implements WithdrawalPurgeStep {

    private final FriendService friends;
    private final GroupService groups;

    public FriendPurgeStep(FriendService friends, GroupService groups) {
        this.friends = friends;
        this.groups = groups;
    }

    @Override
    public int order() {
        return 55;
    }

    @Override
    public void purge(long memberId) {
        groups.purgeWithdrawn(memberId); // 030 그룹
        friends.purgeWithdrawn(memberId);
    }
}

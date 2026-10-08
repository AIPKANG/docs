package com.team.blog.post.application;

import com.team.blog.account.application.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;

/** 023 익명 처리 단계 10: 내 글 전부(휴지통 포함) 완전 삭제, 글에만 쓰던 사진은 끊김 표시. */
@Component
public class PostPurgeStep implements WithdrawalPurgeStep {

    private final PostTrashService trashService;

    public PostPurgeStep(PostTrashService trashService) {
        this.trashService = trashService;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void purge(long memberId) {
        trashService.purgeAllByAuthor(memberId);
    }
}

package com.team.blog.note.application;

import com.team.blog.account.application.WithdrawalPurgeStep;
import org.springframework.stereotype.Component;

/** 탈퇴 정리: 짧은 기록 삭제(글 정리 10 다음). */
@Component
public class NotePurgeStep implements WithdrawalPurgeStep {

    private final NoteService notes;

    public NotePurgeStep(NoteService notes) {
        this.notes = notes;
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public void purge(long memberId) {
        notes.purgeWithdrawn(memberId);
    }
}

package com.team.blog.media.application;

import com.team.blog.account.application.WithdrawalPurgeStep;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 023 익명 처리 단계 40: 내가 올린 사진 전부 끊김 표시(프로필 연결은 90단계가 비운다). 사진 정리 작업이 7일 뒤 지운다. */
@Component
public class ImagePurgeStep implements WithdrawalPurgeStep {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ImagePurgeStep(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void purge(long memberId) {
        jdbc.update("UPDATE image SET detached_at = COALESCE(detached_at, ?) WHERE uploader_id = ?",
                Timestamp.from(clock.instant()), memberId);
    }
}

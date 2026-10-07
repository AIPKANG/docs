package com.team.blog.account.application;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/** 로그인 시점 계정 상태 판정 결과({@link AccountStatusChecker}). */
public sealed interface LoginStatus extends Serializable {

    record Active() implements LoginStatus {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** @param endsAt null이면 영구 정지 */
    record Suspended(Instant endsAt, String reason) implements LoginStatus {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    record WithdrawnPending() implements LoginStatus {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}

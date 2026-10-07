package com.team.blog.shared.security;

import java.util.Optional;

/**
 * {@link AccountGuard}가 쓰기 가능 여부를 판단하려고 읽는 계정 상태(SPI). account 모듈이 구현한다 —
 * shared가 account의 테이블을 직접 읽지 않게 한다(헌법 I). 구현은 PK 조회 1회.
 */
public interface AccountStatusLookup {

    /**
     * @param withdrawalPending 탈퇴 유예({@code status='WITHDRAWN' AND deleted_at IS NULL})
     * @param anonymized        익명 처리됨({@code deleted_at IS NOT NULL})
     * @param emailVerified     {@code auth_identity.email_verified_at IS NOT NULL}
     */
    record WriteStatus(boolean withdrawalPending, boolean anonymized, boolean emailVerified) {
    }

    Optional<WriteStatus> find(long memberId);
}

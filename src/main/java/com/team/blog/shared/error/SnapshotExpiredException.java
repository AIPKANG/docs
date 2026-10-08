package com.team.blog.shared.error;

/** 보던 트렌딩 순위가 보관 시간이 지나 사라짐 → 410 {@code SNAPSHOT_EXPIRED}(019 FR-011). */
public class SnapshotExpiredException extends RuntimeException {

    public static final String CODE = "SNAPSHOT_EXPIRED";

    public SnapshotExpiredException() {
        super(CODE);
    }
}

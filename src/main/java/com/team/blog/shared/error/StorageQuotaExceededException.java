package com.team.blog.shared.error;

/** 저장 공간(1GB) 초과 → 409 {@code STORAGE_QUOTA_EXCEEDED}(23 §3). */
public class StorageQuotaExceededException extends RuntimeException {

    public static final String CODE = "STORAGE_QUOTA_EXCEEDED";

    public StorageQuotaExceededException() {
        super(CODE);
    }
}

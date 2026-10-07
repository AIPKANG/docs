package com.team.blog.shared.error;

/**
 * 수동 저장이 서버 버퍼에는 들어갔지만 DB 반영이 실패함 → 503 {@code SAVE_DELAYED} + {@code version}
 * (004 research R-5). 내용은 버퍼에 있어 다음 반영 주기에 들어간다.
 */
public class SaveDelayedException extends RuntimeException {

    public static final String CODE = "SAVE_DELAYED";

    private final long version;

    public SaveDelayedException(long version, Throwable cause) {
        super(CODE, cause);
        this.version = version;
    }

    public long getVersion() {
        return version;
    }
}

package com.team.blog.shared.error;

/** 같은 발행 요청 식별자가 아직 처리 중 → 409 {@code IN_PROGRESS}(05 §6). 브라우저는 1초 뒤 같은 식별자로 재시도한다. */
public class PublishInProgressException extends RuntimeException {

    public static final String CODE = "IN_PROGRESS";

    public PublishInProgressException() {
        super(CODE);
    }
}

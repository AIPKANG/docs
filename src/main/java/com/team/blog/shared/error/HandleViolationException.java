package com.team.blog.shared.error;

import com.team.blog.account.domain.HandleViolation;

/**
 * 블로그 주소 규칙 위반 → 400 {@code {code, suggestion}}. 금칙어 위반이어도 걸린 단어는 담지 않는다(SC-006).
 */
public class HandleViolationException extends RuntimeException {

    private final HandleViolation code;
    private final String suggestion;

    public HandleViolationException(HandleViolation code) {
        this(code, null);
    }

    public HandleViolationException(HandleViolation code, String suggestion) {
        super(code.name());
        this.code = code;
        this.suggestion = suggestion;
    }

    public HandleViolation getCode() {
        return code;
    }

    /** 대안 주소({@code HANDLE_RESERVED}·{@code HANDLE_DUPLICATE}일 때만), 없으면 null. */
    public String getSuggestion() {
        return suggestion;
    }
}

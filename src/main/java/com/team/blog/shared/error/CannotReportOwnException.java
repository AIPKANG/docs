package com.team.blog.shared.error;

/** 자기 글·댓글 신고 → 400 {@code CANNOT_REPORT_OWN}(022 FR-005), 관리자 자기 것 처리·관리자 정지 → 400 {@code CANNOT_MODERATE_OWN}. */
public class CannotReportOwnException extends RuntimeException {

    public static final String CODE = "CANNOT_REPORT_OWN";
    public static final String MODERATE_OWN = "CANNOT_MODERATE_OWN";

    private final String code;

    public CannotReportOwnException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

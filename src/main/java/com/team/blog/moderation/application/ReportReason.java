package com.team.blog.moderation.application;

import java.util.Arrays;
import java.util.Optional;

/** 신고 사유(43 §2, {@code ck_report_reason})·숨김 사유. 화면 이름을 함께 둔다. */
public enum ReportReason {
    SPAM("스팸·광고"), ABUSE("욕설·혐오"), SEXUAL("음란·선정"), PRIVACY("개인정보 노출"), COPYRIGHT("저작권 침해"), OTHER("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<ReportReason> parse(String value) {
        return Arrays.stream(values()).filter(r -> r.name().equals(value)).findFirst();
    }

    public static String labelOf(String value) {
        return parse(value).map(ReportReason::label).orElse("기타");
    }
}

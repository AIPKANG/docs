package com.team.blog.notification.application;

import java.util.List;

/** 알림 종류(25 §2). 끌 수 있는 종류는 {@link #MUTABLE}만. */
public enum NotificationType {
    COMMENT, REPLY, LIKE, FOLLOW, NEW_POST, REPORT_RESOLVED, CONTENT_HIDDEN;

    public static final List<NotificationType> MUTABLE = List.of(COMMENT, REPLY, LIKE, FOLLOW, NEW_POST);

    public boolean mutable() {
        return MUTABLE.contains(this);
    }

    /** 받는 사람이 그 글을 지금 읽을 수 있어야 만드는 종류(FR-008 ⑤). */
    public boolean needsReadablePost() {
        return this == COMMENT || this == REPLY || this == LIKE || this == NEW_POST;
    }
}

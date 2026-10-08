package com.team.blog.notification.application;

import java.util.List;

/** 알림 종류(25 §2). 끌 수 있는 종류는 {@link #MUTABLE}만. */
public enum NotificationType {
    COMMENT, REPLY, LIKE, FOLLOW, NEW_POST, REPORT_RESOLVED, CONTENT_HIDDEN,
    /** 친구 요청(025, 강성찬 개인 확장): 처리해야 하는 요청이라 끌 수 없다. */
    FRIEND_REQUEST,
    /** 친구 공개였던 글이 처음 전체 공개됨(026, 강성찬 개인 확장): 친구에게 응원 알림. "새 글" 설정을 따른다. */
    FIRST_PUBLIC;

    public static final List<NotificationType> MUTABLE = List.of(COMMENT, REPLY, LIKE, FOLLOW, NEW_POST);

    public boolean mutable() {
        return MUTABLE.contains(this);
    }

    /** 받는 사람이 그 글을 지금 읽을 수 있어야 만드는 종류(FR-008 ⑤). */
    public boolean needsReadablePost() {
        return this == COMMENT || this == REPLY || this == LIKE || this == NEW_POST || this == FIRST_PUBLIC;
    }
}

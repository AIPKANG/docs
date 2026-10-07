package com.team.blog.media.domain;

/** 사진 상태({@code image.status}, 04 §4-4). 연결이 끊기면 상태는 그대로 두고 {@code detached_at}을 기록한다. */
public enum ImageStatus {
    TEMP,
    ATTACHED
}

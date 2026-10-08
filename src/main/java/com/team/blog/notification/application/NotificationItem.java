package com.team.blog.notification.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 보여줄 알림 하나(25 §5). 지금 상태로 다시 조회한 값만 담는다(FR-017). 읽을 수 없는 글이면 {@code post.unavailable}이고
 * 제목·댓글 내용·이동 주소가 없다(FR-018). 신고자·관리자 정보는 어느 필드에도 없다(FR-021).
 *
 * @param message 화면에 그대로(글자로만) 넣는 문장
 * @param url     누르면 갈 곳, 없으면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationItem(long id, String type, boolean read, Instant updatedAt, String timeLabel, Actor actor,
                               Integer othersCount, PostRef post, String commentPreview, String result, String message,
                               String url) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Actor(String nickname, String handle, String profileImageUrl, boolean withdrawn) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostRef(String title, String url, Boolean unavailable) {
    }
}

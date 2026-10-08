package com.team.blog.shared.event;

/**
 * 대기 중이던 친구 요청이 끝남(수락·거절·취소, 025). 받은 사람의 안 읽은 요청 알림에서 그 사람을 뺄 뿐, 누구에게도 새 알림을 만들지 않는다
 * (거절은 상대에게 드러나지 않음, 06 §6-2).
 */
public record FriendRequestClosed(long requesterId, long receiverId) implements DomainEvent {
}

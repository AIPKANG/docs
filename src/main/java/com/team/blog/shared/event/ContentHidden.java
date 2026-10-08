package com.team.blog.shared.event;

/**
 * 관리자가 글·댓글을 숨김(022 FR-030). 알림이 커밋 후 작성자에게 "숨겨졌어요"를 보낸다. 신고자·관리자 정보는 담지 않는다.
 *
 * @param commentId 댓글 숨김이면 그 댓글, 글 숨김이면 null
 */
public record ContentHidden(long postId, Long commentId, long authorId) implements DomainEvent {
}

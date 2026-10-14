# Feature Specification: 댓글·답글 좋아요

**Feature Branch**: `089-comment-like` | **Created**: 2026-10-14 | **Status**: Implemented (1.38.0)

**Scope**: **개인 확장**. 구현 PR practicech/blog#132.

## 결정
- 댓글과 답글마다 [답글] 앞에 ♡와 좋아요 수. 누르면 새로고침 없이 ♥로 바뀐다. 내 댓글에는 받은 수(♥ N)만 보인다.
- 규칙은 글 좋아요와 같다: 자기 댓글은 400, 비회원은 로그인 화면으로, 볼 수 없는 비밀 댓글·지운 댓글은 404, 요청 횟수 한도는 글 좋아요와 함께 쓴다.
- 받은 사람에게 LIKE 알림("회원님의 댓글을 좋아해요"). 글 좋아요와 같은 알림 끄기 설정을 따르고 댓글마다 묶는다. 취소하면 묶음에서 빠진다.
- 끄기: `blog.comment.like-enabled=false`.

## 데이터
- `comment_like` 표(Flyway V20, 새 표만). 댓글이 지워지면 CASCADE, 답글 때문에 자리만 남는 댓글도 좋아요를 지운다. 탈퇴 정리에 포함.
- API: `PUT/DELETE /api/comments/{id}/like` → `{liked, likeCount}`(글 좋아요와 같은 응답).

## Success Criteria
- `CommentLikeIT`: 누르기·취소·알림·자기 댓글 400·비회원 401·비밀 댓글 404·삭제 시 정리. 전체 테스트 통과.

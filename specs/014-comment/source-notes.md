# Source Notes: 014-comment

원문: `docs/21-comment.md` (§1 CM-1~CM-13, §2 구조, §3 화면, §4 입력, §5 작성, §6 조회, §7 수정, §8 삭제, §9 숨김, §10 글 상태, §11 탈퇴, §12 제한, §13 개인 확장, §14 완료 기준, §15 ERD)

## plan 단계에서 참고할 기술 결정

- 컬럼: `parent_id`(항상 최상위), `reply_to_member_id`(회원 FK, nullable), `updated_at`은 내용 수정 때만 갱신, 삭제는 `deleted_at` + `content = ''` (21 §2, §7, §8)
- ERD 변경: `reply_to_member_id` 추가, `UNIQUE(post_id, id)` + 복합 FK `(post_id, parent_id) → comment(post_id, id) ON DELETE CASCADE`, `ck_comment_reply_to`, `ck_comment_edited`, 인덱스 `ix_comment_root`/`ix_comment_reply`/`ix_comment_author`(기존 `ix_comment_post`·`ix_comment_parent` 대체). 숨김 컬럼 `hidden_at·hidden_by·hidden_reason`은 43이 추가 (21 §15)
- 1단계 깊이는 CHECK로 표현 불가 → Service 검사 + 완료 기준 3번 테스트. 깊이 설정값 `blog.comment.max-depth` (21 §13, §15)
- API: `POST /api/posts/{postId}/comments` `{content, replyToCommentId}` → 201, `GET /api/posts/{postId}/comments?cursor=`, `GET /api/comments/{rootId}/replies?cursor=`, `PATCH /api/comments/{id}`, `DELETE /api/comments/{id}`, `?around={commentId}` (21 §5~§8)
- 응답 `state`: `NORMAL/DELETED/HIDDEN/WITHDRAWN_AUTHOR`, 커서 `(created_at, id)` Base64URL, 21개 조회 (21 §6)
- 페이지당 SQL 2번(최상위 21 + `row_number() OVER (PARTITION BY parent_id)` 답글 3개) (21 §6)
- 작성 트랜잭션: 부모 `FOR SHARE` → INSERT → `comment_count + 1`(`@Modifying`, 05 J-2) → `CommentCreated`(커밋 후, 20 §3-2, `replyToMemberId` 포함). 삭제는 최상위 `FOR UPDATE` (21 §5, §8)
- 중복 방지 Redis `SET cmt:dedupe:{memberId}:{postId}:{sha256(내용+대상)} {commentId} NX EX 10`, 요청 제한 Redis 카운터 (21 §5, §12)
- 길이는 `char_length`/`codePointCount` (Java `length()` 금지) (21 §4)
- 표시: HTML 이스케이프 + CSS `white-space: pre-line`, `id="comment-{id}"`, SSR `?commentCursor=` (21 §3, §6)
- 비공개 글 응답 `Cache-Control: private, no-store` (06 R-5)
- 삭제 시 `CommentDeleted` → 알림 삭제 (20 §3-2), 숨김 시 알림 삭제 + `CONTENT_HIDDEN` (20 §4-2)
- 탈퇴 정리 SQL 2-a~2-d, `CommentWithdrawalPurgeStep` (21 §11, 13 §3-3) — spec 023 연계

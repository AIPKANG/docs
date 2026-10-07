# Source Notes: 011-manage-posts-trash

원문: `docs/41-manage-posts.md`, `docs/13-delete-withdraw.md` §1(D-1~D-5), §2, §4(post 인덱스), §5(C-POST-5)

## plan 단계에서 참고할 기술 결정

- 화면 주소 `/manage/posts?tab=drafts|published|trash&visibility=public|private` (41 §2, §3-2)
- 목록 API `GET /api/me/posts?tab=…&visibility=…&cursor=…` → `{ items, nextCursor, counts }`, 응답 항목 목록 (41 §5)
- 21개 조회로 다음 페이지 판단, 커서: 임시·발행 `(updated_at, id)`, 휴지통 `(deleted_at, id)` (41 §5)
- counts는 첫 요청만 `GROUP BY` 1번, 작업본 유무는 `post_draft` LEFT JOIN으로 목록 쿼리에 포함 (41 §5, §6)
- 인덱스 `ix_post_manage(author_id, status, updated_at DESC)`, `ix_post_trash(author_id, deleted_at DESC) WHERE deleted_at IS NOT NULL` (41 §6, 13 §4)
- 버튼별 API: `DELETE /api/posts/{id}`(200 `{trashed, purgeAt}` 또는 `{purged}`), `POST /api/posts/{id}/restore`, `DELETE /api/posts/{id}/permanent`, `PATCH /api/posts/{id}/visibility`, `GET /api/me/trash` (41 §4, 13 §2-4)
- JS 없으면 폼 POST 후 같은 탭으로 (SSR) (41 §4)
- 모든 삭제 요청은 `author_id = 현재 사용자` 조건 + `FOR UPDATE` 행 잠금 (13 §2-4)
- 휴지통 이동 직전 Redis 자동 저장분 DB 반영, 커밋 후 Redis 키 삭제 (13 §2-3, 04 §2-4)
- 완전 삭제 SQL: `image.detached_at` 기록(다른 글에서 쓰면 제외) → `DELETE FROM post` + CASCADE(post_tag, comment, post_like, post_image, post_draft). 사진 정리 배치가 7일 뒤 파일 삭제 (13 §2-5, 04 §4-4)
- 휴지통 비우기 배치: 매일 새벽, 100개씩, ShedLock (13 §2-5)
- 누출 방지: `Post`에 `@SQLRestriction("deleted_at IS NULL")`, 목록은 `VisibilityFilter` 공용 조건, `PostAccessPolicy.canRead`가 삭제 여부를 먼저 확인, 권한 매트릭스 테스트에 "휴지통 글" 상태 추가 (13 §2-6)
- 목록 시간 300ms 이내 (41 §6, 02 §6)

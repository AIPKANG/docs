---
description: "005-post-publish 구현 작업 목록"
---

# Tasks: 글 발행·수정

**작업 ID**: 005는 **`T501`부터**. 004·007 클래스를 확장한다.

## Phase 1~2: Setup·Foundational
- [X] T501 `blog.post.max-tags`, `blog.post.publish.idempotency-ttl` 설정, `PostProperties` 확장
- [X] T502 [P] `tag` 모듈: `TagNormalizer`(22 §2) + `TagNormalizerTest`(22 §2-1 예시 전부)
- [X] T503 [P] `PostTagService`·`TagStore`(ON CONFLICT DO NOTHING, post_tag 교체·순서)
- [X] T504 [P] 오류 `PublishInProgressException`(409 `IN_PROGRESS`), `IdempotencyKeyReusedException`(422), 문구
- [X] T505 [P] 사건 `PostPublished`, `PostEdited`
- [X] T506 `RedisAutosaveBuffer.evictUpTo(postId, version)`(Lua), `PostEditStore.lockForPublish/applyPublish/deleteWorkingCopy`

## Phase 3: US1·US2 — 발행과 다시 발행 (P1)
- [X] T507 [P] `PublishIT`: 첫 발행 시각·주소·비로그인 열람·요약·태그 순서, 검증 실패 칸 모두·임시글 유지, `local:` 거부, XSS 본문·제목 상세 무해
- [X] T508 [P] `RepublishIT`: 작업본 → 다시 발행 → 내용·`edited_at`, 주소·`published_at`·`first_public_at`·카운터 유지, 작업본 삭제, 공개 범위 전환
- [X] T509 `PublishValidator`, `PostPublishService`, `PublishCommand`/`PublishResult`, `PostPublishApiController`
- [X] T510 `PostAccessPolicy`, `PostDetailQuery`, `PostDetailController`, `templates/post/detail.html`, `PostDetailIT`
- [X] T511 편집 화면 발행 설정·`publish.js`(비활성·"발행 중…"·재시도·비교 창·칸 오류)

## Phase 4: US3 — 연타 방지 (P1)
- [X] T512 [P] `PublishIdempotencyIT`: 같은 키 동시 20건 → 버전 +1·모두 같은 응답, 처리 중 409, 완료 재응답, 다른 내용 422, 실패 뒤 재시도, Redis 장애 시 두 번째 409
- [X] T513 `PublishIdempotency`(Redis SET NX EX, 완료·실패 처리)

## Phase 5: US4 — 권한 (P1)
- [X] T514 [P] `PublishPermissionIT`: 남의 글·관리자·휴지통 404(값 불변), 401·403, 본문의 작성자 값 무시

## Phase 6: US5·US6 (P2)
- [X] T515 [P] `PublishConflictIT`: 옛 버전 발행 409+server, 발행 중 들어온 새 자동 저장 보존, 늦은 공개 `first_public_at`·껐다 켜도 그대로

## Phase 7: Polish
- [X] T516 quickstart·구현 메모·전체 테스트

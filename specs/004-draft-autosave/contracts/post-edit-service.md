# Contract: post 모듈 공개 Service (004)

다른 표현 계층(REST를 쓰는 팀원)과 005(발행)·011(내 글 관리)이 쓰는 공개 메서드. 모든 쓰기 메서드는 첫 줄에서 `AccountGuard.requireWritable`(401/403) 후 소유 판정(404)을 한다.

## `PostDraftService`

| 메서드 | 결과 | 예외 |
|---|---|---|
| `long create(Optional<CurrentUser>, String title, String contentMd)` | 새 `DRAFT` 행(버전 0, 공개 범위 = `AccountSettingsService.defaultVisibility`) | 401/403, `PostContentException`(400) |
| `EditingContent editing(Optional<CurrentUser>, long postId)` | 현재 내용(research R-2) | 404 |
| `SaveResult autosave(Optional<CurrentUser>, long postId, SaveCommand)` | 요청 제한 → 버퍼 저장(Redis, 장애 시 DB) | 404, `EditConflictException`(409), `RateLimitedException`(429), 400 |
| `SaveResult save(Optional<CurrentUser>, long postId, SaveCommand)` | 버퍼 저장 + 즉시 DB 반영 | 404, 409, 400, `SaveDelayedException`(503) |
| `void discardWorkingCopy(Optional<CurrentUser>, long postId)` | 작업본·버퍼 삭제, `post.edit_version` = 현재 버전 | 404(임시글 포함) |

## `PostManageQuery` (011이 넓힘)

| 메서드 | 결과 |
|---|---|
| `List<MyPostRow> myPosts(Optional<CurrentUser>)` | 휴지통 밖 내 글, 최근 수정 순, `editing`(수정 중) 포함 |
| `boolean isEditing(long postId)` | 작업본 또는 더 새 버퍼가 있는지 |

## 내부 구성 요소 (005가 재사용)

| 이름 | 역할 |
|---|---|
| `AutosaveBuffer` (인터페이스) / `RedisAutosaveBuffer` | Lua 저장(`save`), 읽기(`read`), 반영 후 조건부 dirty 제거(`markFlushed`), 삭제(`evict`), dirty 목록(`dirtyBatch`) |
| `PostEditStore` (JDBC) | 소유·상태·버전 조회, 조건부 반영 SQL(임시글/작업본), DB 경로 저장(`FOR UPDATE`), 작업본 삭제 |
| `AutosaveFlusher` | 글 하나 반영(수동 저장·반영 작업 공용) |
| `AutosaveFlushJob`, `EmptyDraftCleanupJob` | 예약 실행 + `JobLock`(Redis `SET NX PX`) |
| `RedisCircuit` | Redis 실패 후 일정 시간 DB 경로로 |

005 발행이 지킬 것: 발행 트랜잭션에서 버전 확인(`max` 규칙) → `post`에 반영·`edit_version` 갱신·`post_draft` 삭제 → **커밋 후** `AutosaveBuffer.evict(postId)`.

---

description: "004-draft-autosave 구현 작업 목록 (post 모듈 시작)"
---

# Tasks: 임시저장·자동 저장

**Input**: Design documents from `/specs/004-draft-autosave/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (web-routes.md, post-edit-service.md), quickstart.md, `.specify/memory/constitution.md`

**작업 ID 규칙**: 002 `T001`~, 001 `T101`~, 003 `T201`~, 004는 **`T301`부터**. 001~003 클래스(`AccountGuard`, `AccountSettingsService`, `RedisRateLimiter`, `GlobalExceptionHandler`, `ErrorResponse`, `IntegrationTestBase`, `DatabaseCleaner`, `MemberFixtures`, `layout/base.html`)를 **확장**하고 다시 만들지 않는다.

**Tests**: 헌법 VI. 각 스토리의 통합 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다. Testcontainers PostgreSQL 18 + Redis(003 기반 그대로).

**Organization**: 스토리 순서 US1(P1) → US2(P1) → US3(P2) → US4(P2) → US5(P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 가능(다른 파일, 끝나지 않은 작업에 의존하지 않음)
- 경로는 저장소 루트 기준, 패키지 `com.team.blog`

---

## Phase 1: Setup

- [X] T301 `post` 모듈 패키지 `post/{web,application,domain,infra}`의 `package-info.java`(02 §3, 헌법 I: account는 공개 Service로만)
- [X] T302 [P] `application.yml`에 `blog.post.*` 설정(data-model §6). `application-test.yml`에 `blog.post.autosave.flush-enabled: false`, `blog.post.empty-draft-cleanup.enabled: false`
- [X] T303 [P] `post/application/PostProperties.java`(`@ConfigurationProperties("blog.post")`, 중첩 record `Autosave`, `Editor`, `EmptyDraftCleanup`)

## Phase 2: Foundational

- [X] T304 [P] `post/domain/PostStatus`(DRAFT/PUBLISHED), `post/domain/PostContentRules`(제목 줄바꿈·제어 문자 → 공백, 제목 100자·본문 10만 자 코드 포인트 검사, `baseVersion` ≥ 0) + `post/unit/PostContentRulesTest`
- [X] T305 [P] 새 글 행 만들기 — 구현에서는 JPA 엔터티 대신 `PostEditStore.insertDraft`(JDBC `RETURNING id`)로 했다(조건부 반영 SQL과 같은 저장소, 005가 엔터티가 필요하면 그때 추가)
- [X] T306 [P] 오류: `shared/error/EditConflictException`(서버 내용), `PostContentException`(코드), `PayloadTooLargeException`, `SaveDelayedException`(version) / `ErrorResponse`에 선택 필드 `server`(`ServerContent`), `version` 추가 / `GlobalExceptionHandler` 매핑 409·400·413·503 / `messages.properties` 문구
- [X] T307 `post/application/AutosaveBuffer` 인터페이스 + `BufferedContent`, `BufferSaveOutcome`(accepted/conflict/forbidden + version) / `post/infra/RedisAutosaveBuffer`(Lua 저장 `max(redis, db)`·소유 확인·EXPIRE·SADD, 읽기, 조건부 SREM, evict, `SSCAN` dirty 배치)
- [X] T308 `post/infra/PostEditStore`(JDBC): `findOwned(postId, memberId)`(휴지통 제외, 상태·post 버전·작업본 버전·내용), `flushDraft`, `flushWorkingCopy`(research R-4 SQL), `saveDirect`(FOR UPDATE, R-6), `discardWorkingCopy`
- [X] T309 [P] `post/application/JobLock` + `post/infra/RedisJobLock`(`SET NX PX`, 토큰 비교 삭제 Lua), `post/infra/RedisCircuit`(실패 후 `redis-retry-after` 동안 열림)
- [X] T310 [P] `src/test/java/com/team/blog/support/PostFixtures`(`draft(authorId, title, content, version)`, `publish(postId)` SQL, `trash(postId)`, `setTimes(postId, created, updated)`, `workingCopy(postId, …)`) — `IntegrationTestBase` `@Import`에 추가

**Checkpoint**: 기반 준비 완료.

## Phase 3: US1 — 자동 저장·이어 쓰기 (P1) 🎯 MVP

### Tests
- [X] T311 [P] [US1] `post/integration/DraftCreateIT`: `POST /write` 303·행 DRAFT v0·기본 공개 범위 따름, `POST /api/posts` 201(제목·본문 포함), 비회원 401·인증 전 403
- [X] T312 [P] [US1] `post/integration/AutosaveIT`: 200 `{version}`·Redis Hash·dirty, 연속 저장 버전 증가, 태그 등 다른 필드 무시, `GET …/editing`이 버퍼 내용을 돌려줌
- [X] T313 [P] [US1] `post/integration/AutosaveFlushIT`: 반영 후 `post` 내용·버전·`updated_at`, `content_html` 비어 있음, Redis 지워도 다시 열면 내용 유지(SC-001), DB가 더 새 버전이면 덮지 않음, 반영 중 새 버전이 들어오면 dirty 유지, 잠금이 잡혀 있으면 건너뜀
- [X] T314 [P] [US1] `post/integration/ManualSaveIT`: 수동 저장 즉시 DB 반영·dirty 제거, 409 규칙 같음, 요청 제한 없음
- [X] T315 [P] [US1] `post/integration/EditorPageIT`: `/write/{id}` 작성자만(남의 글 404), 상태 JSON 이스케이프(`</script>` 제목), 머리글 [새 글]·[내 글], `/manage/posts` 목록·"(제목 없음)"

### Implementation
- [X] T316 [US1] `post/application/PostDraftService.create/editing/autosave/save`(권한 → 소유 → 규칙 → 요청 제한(자동만) → 버퍼, 수동은 `AutosaveFlusher.flushOne`) + `EditingContent`, `SaveCommand`, `SaveResult`
- [X] T317 [US1] `post/application/AutosaveFlusher`(글 하나 반영 + 조건부 dirty 제거), `AutosaveFlushJob`(`fixedDelay`, 잠금, 배치)
- [X] T318 [US1] `post/web/PostDraftApiController`(POST /api/posts, GET editing, PUT autosave, PUT draft), `post/web/AutosaveBodyLimitFilter`(1MB → 413)
- [X] T319 [US1] `post/web/EditorController`(POST /write, GET /write/{id}), `post/application/PostManageQuery` + `MyPostRow`, `post/web/ManagePostsController`, 템플릿 `post/editor.html`, `post/manage.html`, `layout/base.html` 머리글
- [X] T320 [US1] `static/js/editor/draft-store.js`(IndexedDB `blog-drafts`/`kv`), `autosave.js`(1초 로컬·3초/30초 서버·바뀐 경우만·탭당 1요청·상태 문구), `editor.js`(묶기·수동 저장)

## Phase 4: US2 — 네트워크·서버 장애 (P1)

- [X] T321 [P] [US2] `post/integration/AutosaveRedisDownIT`: 실패하는 `AutosaveBuffer`를 주입한 컨텍스트에서 자동 저장 200·DB 반영·버전 확인 409, 회로가 열린 동안 Redis 호출 없음
- [X] T322 [P] [US2] `post/integration/AutosavePermissionIT`: 42 §5-2 표(비회원 401, 인증 전 403, 남의 글·없는 글·휴지통·관리자 404, 내용·버전 불변 — SC-008), 6초 안 두 번째 자동 저장 429 `Retry-After`, 1MB 초과 413, 제목·본문 길이 400
- [X] T323 [US2] `PostDraftService` Redis 실패 → `PostEditStore.saveDirect` 경로 + `RedisCircuit`, 요청 제한 열린 실패
- [X] T324 [US2] `autosave.js`: 실패 재시도(2→60초 + 무작위), 429 `Retry-After`, `online`/`offline`, `visibilitychange`·`pagehide` keepalive 전송, `beforeunload`, 열 때 `dirty`·같은 버전이면 "불러왔어요" 안내, 동기화 상태로 떠나면 로컬 삭제

## Phase 5: US3 — 충돌과 비교 창 (P2)

- [X] T325 [P] [US3] `post/integration/ConcurrentAutosaveIT`: 같은 출발 버전 20건 동시 → 성공 1, 409 19(`server` 내용 = 성공한 내용), 수동 저장·자동 저장 혼합도 같음(SC-006)
- [X] T326 [P] [US3] `src/test/js/diff.test.mjs`: 줄 diff·단어 강조·접기 구간·제목 비교 순수 함수 검사(Node, 빌드 밖)
- [X] T327 [US3] `static/js/editor/diff.js`(줄 LCS, 단어 LCS, 접기, 차이 위치 목록)
- [X] T328 [US3] `editor.js`/`editor.html`: 409 배너·상태, [비교하기]·수동 저장 시 비교 창(`<dialog>`, 좌우/위아래, 이전·다음 차이), 세 선택지(확인 문구 덮어쓰기, 7일 백업 후 불러오기, 새 임시글로 따로 저장), 닫기, 열 때 버전 다르면 즉시 비교 창

## Phase 6: US4 — 발행 글 작업본 (P2)

- [X] T329 [P] [US4] `post/integration/WorkingCopyIT`: 발행 글 저장 → `post_draft`만 바뀌고 `post` 그대로(SC-005), 다시 열면 작업본, 목록 "수정 중", 변경 취소 204·작업본·버퍼 삭제·`post.edit_version` 단조, 취소 뒤 옛 Hash 반영이 작업본을 되살리지 못함, 임시글 취소 404, 남의 글 404
- [X] T330 [US4] `PostDraftService.discardWorkingCopy`(트랜잭션 + 커밋 후 `evict`), `DELETE /api/posts/{id}/working-copy`, `PostManageQuery.isEditing`, 편집 화면 "수정 중" 안내·[변경 취소]

## Phase 7: US5 — 빈 임시글 정리 (P3)

- [X] T331 [P] [US5] `post/integration/EmptyDraftCleanupIT`: 조건 만족 삭제, 제목만/본문만/공백 아님/버퍼 있음/23시간/발행 글/휴지통 → 남음, 잠금 중 건너뜀
- [X] T332 [US5] `post/application/EmptyDraftCleanupService`, `EmptyDraftCleanupJob`(cron, 잠금, Redis 못 닿으면 건너뜀)

## Phase 8: Polish

- [X] T333 [P] `post/unit/PostVersionTest`(현재 버전 계산 규칙)
- [X] T334 [P] 비밀값·내부 정보 누출 점검: 409·404 본문에 다른 회원 정보 없음, 로그에 본문 원문 없음
- [X] T335 quickstart S1~S7 확인(자동 테스트 + 로컬 화면), `research.md`에 구현 메모, `specs/README.md` 표 갱신 없음 확인
- [X] T336 전체 테스트 `./gradlew test`

## Dependencies

- Phase 1 → 2 → US1 → US2 → US3 → US4 → US5 → Polish. US3·US4는 US1 API에만 의존해 순서를 바꿀 수 있다.

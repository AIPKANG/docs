# Implementation Plan: 임시저장·자동 저장

**Branch**: `004-draft-autosave` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-draft-autosave/spec.md`

## Summary

Tier A 공통 필수 C-POST-2(임시저장)를 구현한다. [새 글]을 누르면 `DRAFT` 행(편집 버전 0)을 바로 만들고, 편집 화면은 **브라우저 IndexedDB(1초) → 서버 Redis 버퍼(3초/최대 30초, 바뀐 경우만) → PostgreSQL(1분 반영 + 수동 저장 즉시)** 3단계로 저장한다(04 §2). 서버는 글마다 편집 버전을 두고 Redis Lua로 "출발 버전 = 현재 버전일 때만 저장 +1"을 원자 처리해, 다른 탭·기기가 먼저 저장했으면 409와 서버 내용을 돌려준다. 브라우저는 편집을 막지 않고 배너와 비교 창(줄·단어 diff, 결과가 이름에 드러나는 세 선택지)을 보여준다. 발행 글은 `post_draft` 작업본에만 저장해 독자는 마지막 발행본을 보고, [변경 취소]로 작업본을 버린다. Redis 장애 때는 DB 행 잠금으로 같은 버전 확인을 하며 바로 저장한다. 빈 임시글은 매일 새벽 정리한다. 새 `post` 모듈을 만들고 공통 ERD(V1)는 그대로 쓴다. 발행 자체는 005.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1, Gradle Wrapper 9.8.0 (Kotlin DSL) — 001~003과 같음

**Primary Dependencies**: Spring Web MVC, Thymeleaf, Spring Security 7, Spring Data Redis(`StringRedisTemplate`, Lua `RedisScript`), Spring JDBC(`JdbcTemplate`, 조건부 반영 SQL), Spring Data JPA(글 행 생성), `@Scheduled`. **새 의존성 없음.** 화면 JS는 의존성 없는 순수 JS(IndexedDB, `fetch keepalive`, 자체 LCS diff — research R-10)

**Storage**: PostgreSQL 18(V1 `post`·`post_draft` 그대로, 새 마이그레이션 없음) / Redis(`autosave:post:{id}` Hash, `autosave:dirty` Set, 실행 잠금, 요청 제한) / 브라우저 IndexedDB(`blog-drafts`)

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers(PostgreSQL 18·Redis — 003 기반 그대로). Redis 장애는 실패하는 버퍼 구현을 주입한 별도 컨텍스트로. diff 순수 함수는 Node 자체 검사 스크립트(빌드 밖)

**Target Platform**: Linux 서버(Docker Compose), 브라우저 375px~데스크톱(IndexedDB 지원, 미지원이면 메모리만 쓰고 안내)

**Project Type**: web-service (모듈러 모놀리스, SSR + 편집 화면 JSON API)

**Performance Goals**: 자동 저장 요청 서버 처리 수 ms(PK 조회 1 + Lua 1). 반영 작업 1분마다 dirty 최대 500건, 글당 UPDATE/UPSERT 1. 정리 작업 부분 인덱스 없이도 `ix_post_manage`(author_id, status, updated_at) 범위 밖 순차 조회 — 하루 1회라 허용

**Constraints**: 서버는 어느 쪽 내용도 몰래 덮어쓰지 않음(SC-004), 버전 확인·저장 원자적(SC-006), 옛 버전이 새 버전을 덮지 않음(조건부 SQL), 다중 서버에서 반영 1회(실행 잠금), 사용자당 5초 1회·1MB, 정책 수치는 설정값, 렌더링은 발행 때만

**Scale/Scope**: 회원 수천~1만, 동시 편집 글 수백. 화면 3개(편집, 내 글 최소 목록, 머리글 진입), JSON API 5개, 예약 작업 2개

미정 항목(NEEDS CLARIFICATION)은 없다. 원문에 없는 세부는 [research.md](./research.md)에서 기본값을 정했고(사용자 지시: 질문 없이 진행), 팀 확인 권장 항목은 U-1~U-5로 남겼다.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 확인 내용 | Phase 0 전 | Phase 1 후 |
|---|---|---|---|
| I. 하나의 배포 단위, 모듈러 모놀리스 | 같은 앱에 새 `post` 모듈. account는 공개 Service(`AccountSettingsService.defaultVisibility`, `AccountGuard`)만 부름. 버전·소유·작업본 규칙은 Service/domain에 | PASS | PASS |
| II. 공통은 바꾸지 않고, 확장은 추가만 (NON-NEGOTIABLE) | 새 테이블·컬럼·마이그레이션 없음(V1에 `edit_version`, `post_draft` 있음). 간격·주기·TTL·제한·백업 기간·정리 시각 모두 `blog.post.*` 설정값. `ErrorResponse`는 선택 필드 `server`·`version`만 추가 | PASS | PASS |
| III. 서버가 권한을 지킨다 (NON-NEGOTIABLE) | 작성자는 인증 정보에서만. 남의 글·없는 글·휴지통 = 같은 404(관리자 포함, 42 §5-2). Redis Hash의 `memberId`도 Lua에서 다시 확인. 화면에서 숨긴 [변경 취소]도 Service가 판정 | PASS | PASS |
| IV. 사용자 입력은 안전하게 보여준다 | 임시글은 원문만 저장, 렌더링·정화는 발행 때(005·007). 편집 화면·목록·비교 창은 `textContent`/`th:text`로만 넣음. 초기 상태 JSON은 `<script type="application/json">`에 이스케이프. CSP 변경 없음(외부 스크립트 없음) | PASS | PASS |
| V. 부가 기능은 핵심을 막지 않는다 | 트랜잭션 안 외부 호출 없음(Redis 삭제는 커밋 후). Redis 장애에도 저장 성공(DB 경로). 같은 요청 반복은 버전 확인으로 한 번만 반영 | PASS | PASS |
| VI. 실제 환경으로 검증한다 | 권한·소유·동시성·반영 경합을 Testcontainers PostgreSQL·Redis 통합 테스트로. 수용 시나리오를 [quickstart.md](./quickstart.md) S1~S7로 | PASS | PASS |
| 기술 제약 표 | Redis AOF `everysec`·`noeviction`(compose 001부터), PostgreSQL, Flyway 변경 없음 | PASS | PASS |

**결과: 위반 없음.** Complexity Tracking 기재 사항 없음.

Phase 1 재확인 메모
- 04 §2-3과 다른 점: 현재 버전을 `max(Redis, DB)`로 본다(research R-2). 장애 복구 뒤 옛 키 문제를 막는 보강이고 원칙 위반 아님.
- 실행 잠금은 ShedLock 대신 Redis `SET NX PX`(R-4, U-1). 새 테이블이 필요 없어 헌법 II와 맞다.
- 011과의 경계: `/manage/posts` 최소 목록만 만들고 011이 넓힌다(U-3).
- 005와의 경계: 발행은 이 설계의 버전 규칙·`AutosaveBuffer.evict`(커밋 후)를 쓴다(contracts/post-edit-service.md).

## Project Structure

### Documentation (this feature)

```text
specs/004-draft-autosave/
├── plan.md              # 이 파일
├── research.md          # Phase 0 (R-1~R-13, U-1~U-5)
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── web-routes.md         # 화면·JSON API·브라우저 동작
│   └── post-edit-service.md  # post 공개 Service·내부 구성
├── checklists/requirements.md
├── source-notes.md
├── spec.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
src/main/java/com/team/blog/
├── post/                         (새 모듈)
│   ├── web/            EditorController(/write, /write/{id}), ManagePostsController(/manage/posts),
│   │                   PostDraftApiController(/api/posts, …/editing, …/autosave, …/draft, …/working-copy),
│   │                   AutosaveBodyLimitFilter(413)
│   ├── application/    PostDraftService, PostManageQuery, AutosaveFlusher, AutosaveFlushJob, EmptyDraftCleanupService,
│   │                   EmptyDraftCleanupJob, PostProperties, EditingContent, SaveCommand, SaveResult, MyPostRow,
│   │                   AutosaveBuffer(인터페이스), BufferedContent, JobLock(인터페이스)
│   ├── domain/         Post(엔터티, 생성용), PostStatus, PostContentRules
│   └── infra/          PostRepository, PostEditStore(JDBC), RedisAutosaveBuffer(Lua), RedisJobLock, RedisCircuit
└── shared/
    ├── error/          EditConflictException, PostContentException, PayloadTooLargeException, SaveDelayedException,
    │                   ErrorResponse(+server, version) → GlobalExceptionHandler
    └── web/            LayoutModelAdvice(변경 없음)

src/main/resources/
├── application.yml                     blog.post.*
├── messages.properties                 004 오류·안내 문구
├── templates/post/{editor,manage}.html, templates/layout/base.html(머리글 [새 글]·[내 글])
└── static/js/editor/{draft-store.js, autosave.js, diff.js, editor.js}

src/test/java/com/team/blog/
├── post/unit/          PostContentRulesTest, PostVersionTest
├── post/integration/   DraftCreateIT, AutosaveIT, AutosavePermissionIT, ConcurrentAutosaveIT, AutosaveFlushIT,
│                       ManualSaveIT, WorkingCopyIT, AutosaveRedisDownIT, EmptyDraftCleanupIT, EditorPageIT
└── support/            PostFixtures
src/test/js/diff.test.mjs
```

**Structure Decision**: 001~003과 같은 단일 Gradle 프로젝트, 02 §3의 `com.team.blog.post`. 편집 버전·작업본·버퍼는 005 발행이 그대로 쓰도록 `post` 안에 둔다.

## Complexity Tracking

해당 없음 (Constitution Check 위반 없음).

## Phase 요약

- **Phase 0** → [research.md](./research.md): R-1~R-13, 남은 확인 U-1~U-5.
- **Phase 1** → [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).
- **에이전트 컨텍스트 갱신**: 001~003과 같이 실행하지 않음(`CLAUDE.md`를 만들지 않음).
- **구현 순서 제안**: 기반(설정·버퍼·저장소·오류) → US1(새 글·자동 저장·반영·수동 저장·편집 화면) → US2(오프라인·재시도·Redis 장애 경로) → US3(충돌·비교 창) → US4(작업본·변경 취소·목록 표시) → US5(빈 임시글 정리).
- **다음 단계**: `/speckit-tasks`.

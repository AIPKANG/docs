# Research: 글 발행·수정 (005-post-publish)

**Phase 0 산출물** · 2026-10-07 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/05-publish.md`, `docs/22-tag.md` §2·§4, `docs/10-post-list.md` §2-1·§6, `docs/42-permission-matrix.md` §3·§5, 004·007 구현

사용자 지시: 질문 없이 기본값을 정한다. 004(편집 버전·작업본·버퍼)와 007(렌더러·제목 정리)을 그대로 쓴다.

## R-1. 처리 순서 (05 §7, FR-010~FR-017)
- **Decision**: `PostPublishService.publish(user, postId, idempotencyKey, PublishCommand)`:
  1. 권한(`AccountGuard.requireWritable` 401/403) → 소유(404) → 멱등 키(R-4).
  2. 트랜잭션 밖: 입력 검증(R-2, 실패 칸 모두) → `ContentRenderer.render` → 요약·이미지 목록.
  3. 트랜잭션(`TransactionTemplate`): `SELECT … FOR UPDATE`(작성자·휴지통 조건) → 현재 버전 = max(버퍼, 작업본, 글)(004 R-2) ≠ `baseVersion`이면 409 `EDIT_CONFLICT`+`server` → 태그 확정(R-3) → `UPDATE post`(05 §7 ⑦: `published_at = COALESCE`, `first_public_at` CASE, `edited_at` 다시 발행일 때만, `edit_version = 현재 + 1`, `render_version`, `content_html`, `excerpt`, `thumbnail_url`, `visibility`) → `DELETE post_draft` → 이벤트 `PostPublished`/`PostEdited` 등록.
  4. 커밋 후: 버퍼를 "버전 ≤ 확인한 현재 버전"일 때만 삭제(Lua, FR-016) → 멱등 응답 저장.
- 004 응답 코드 `EDIT_CONFLICT`를 그대로 쓴다(05의 `VERSION_CONFLICT`와 같은 의미, 004 비교 창 재사용). 같은 의미의 코드를 둘 두지 않는다.
- **Rationale**: 렌더링(CPU)을 잠금 밖에서 끝내 트랜잭션을 짧게(05 §7). 판정 순서 42 §3.

## R-2. 입력 검증 (FR-003~FR-008)
- **Decision**: 제목 `PostTitleRules.clean` 후 1~100자(`TITLE_REQUIRED`/`TITLE_TOO_LONG`), 본문 `strip` 후 1자 이상·원문 10만 자 이하(`CONTENT_REQUIRED`/`CONTENT_TOO_LONG`), 태그(R-3), 공개 범위 `PUBLIC`/`PRIVATE`(`INVALID_VISIBILITY`), 본문의 `local:` 이미지·링크 주소(`](local:`)면 `PENDING_IMAGES`. 실패는 모두 모아 400 `VALIDATION_FAILED` + `errors[]`(003의 `ProfileValidationException`·`FieldError` 형식을 공용으로 씀). 렌더링 실패는 400 `CONTENT_TOO_COMPLEX`.
- 저장되는 제목은 정리한 값, 본문은 원문(줄바꿈만 004 규칙으로 정리).

## R-3. 태그 (FR-005, 22 §2·§4)
- **Decision**: 새 `tag` 모듈. `tag.domain.TagNormalizer`(22 §2 ①~⑨: NFKC → 숨은·방향·제어 문자 제거 → 공백 제거 → 앞 `#` 제거 → 소문자(ROOT) → 공백 묶음 `-` → `-` 정리 → `[가-힣a-z0-9._+#-]{1,30}` + 한글·영문·숫자 1자 이상 → 금칙어(001 `BannedWordFilter`)). 코드 `INVALID_TAG`, `TAG_TOO_LONG`, `TAG_BANNED_WORD`, 목록은 중복 제거 후 `blog.post.max-tags`(10) 초과면 `TOO_MANY_TAGS`. 오류 칸 이름은 `tags[i]`.
- 저장 `tag.application.PostTagService.replace(postId, names)`(발행 트랜잭션 안, 같은 트랜잭션 전파): `INSERT INTO tag(name) … ON CONFLICT (name) DO NOTHING` → id 조회 → `DELETE post_tag WHERE post_id` → 순서대로 `position` 0..n. 태그 목록 화면·자동완성은 013.

## R-4. 멱등 키 (FR-019, FR-020)
- **Decision**: 헤더 `Idempotency-Key`(1~64자 `[A-Za-z0-9-]`, 없거나 형식 밖이면 400 `INVALID_REQUEST`). Redis `idem:publish:{memberId}:{key}` = `IN_PROGRESS|{hash}`, `SET NX EX 600`. hash = SHA-256(postId, 정리 전 요청 본문 JSON의 정렬된 값). 이미 있으면: hash 다르면 422 `IDEMPOTENCY_KEY_REUSED`, `IN_PROGRESS`면 409 `IN_PROGRESS`, 완료면 저장된 200 응답 그대로. 성공 후 `DONE|{hash}|{응답 JSON}`으로 바꾸고(남은 TTL 유지 대신 10분 다시 설정), 실패(예외)면 키 삭제. Redis 장애면 멱등 확인을 건너뛰고 행 잠금·버전 확인에 맡긴다(R-1, 두 번째는 409).
- **Rationale**: 05 §6.

## R-5. 상태 전이·시각 (FR-001, FR-012~FR-015)
- **Decision**: 발행 → 임시글 되돌리기 API 없음. `published_at`은 처음 한 번, `first_public_at`은 처음 발행+공개일 때 한 번, `edited_at`은 다시 발행 때마다. 조회수·좋아요·댓글 컬럼은 UPDATE에 넣지 않는다. 004 반영 작업은 `edited_at`을 바꾸지 않는다(이미 그렇다).

## R-6. 사진 연결·썸네일 (FR-009 일부)
- **Decision**: 썸네일 = 렌더러가 찾은 첫 우리 저장소 이미지 주소. 글 사진 업로드(008) 전이라 `post_image` 동기화와 640px 썸네일 주소 선택은 008이 이 지점(`PublishImageLinker` 인터페이스, 기본 구현은 아무것도 안 함)에 더한다(10 §6 "썸네일이 없는 옛 사진은 원본 주소로 대체"와 같은 결과).

## R-7. 사건 (FR-017)
- **Decision**: `shared.event.PostPublished(postId, authorId, visibility, firstPublicAt)`·`PostEdited(postId, authorId, visibility)`를 트랜잭션 안에서 발행하고 구독자는 `@TransactionalEventListener(AFTER_COMMIT)`. 이번에는 구독자가 없다(알림·검색은 017·020). 버퍼 정리는 서비스가 커밋 후 직접 하고 실패는 로그만(다음 편집 열기 때 버전 규칙상 무시됨).

## R-8. 글 상세 최소 화면 (FR-002, FR-014, FR-023)
- **Decision**: `GET /@{handle}/posts/{id}` — 010이 넓힌다. 볼 수 있음 판정은 `post.application.PostAccessPolicy` 한 곳(헌법 III): 발행+공개 → 누구나, 발행+비공개·임시글 → 작성자만(배지), 휴지통·없음·주소의 블로그와 작성자가 다름 → 404. 보여주는 것: 제목(글자), 작성자, 최초 발행일, "수정됨 · 10월 3일"(`edited_at`), 태그, 정화된 본문(`th:utext`, 007 정화 결과만), 작성자에게만 [수정](→ `/write/{id}`), 임시글이면 "임시저장" 배지, 수정 중이면 "수정 중" 안내(작성자에게만).
- **Rationale**: SC-001·SC-003을 화면으로 확인하려면 상세가 필요하다. 조회수·댓글·좋아요·OG는 010·014~016.

## R-9. 발행 화면 (FR-018)
- **Decision**: 편집 화면에 [발행] → 발행 설정 영역(공개 범위 라디오(기본: 글의 현재 공개 범위), 태그 입력 칩, [발행]). 누르면 버튼 비활성 + "발행 중…", `crypto.randomUUID()` 키, 409 `IN_PROGRESS`면 1초 뒤 같은 키로 재시도, 409 `EDIT_CONFLICT`면 004 비교 창, 400이면 칸별 문구, 200이면 이 기기 임시 데이터 삭제 후 글 주소로 이동. 발행 전 자동 저장 대기분은 먼저 보내지 않는다 — 발행 요청 자체가 최신 내용과 `baseVersion`을 담는다.

## R-10. 테스트 (헌법 VI)
- 통합: 첫 발행(시각·주소·비로그인 열람), 검증 칸 모두·임시글 유지, 다시 발행(유지 값·`edited_at`·작업본 삭제·공개 범위 전환), 늦은 공개(`first_public_at`), 멱등 동시 20건·처리 중·완료 재응답·422·실패 후 재시도·Redis 장애, 권한 표(404 불변·401·403·관리자), 버전 충돌·발행 중 들어온 자동 저장 보존, XSS 본문·제목 발행 후 상세 HTML 검사, 상세 접근(임시글·비공개·휴지통). 단위: `TagNormalizer` 22 §2-1 예시 전부.

## 남은 확인 사항
| # | 내용 | 기본값 |
|---|---|---|
| U-1 | 충돌 코드 `EDIT_CONFLICT`(004)로 통일 | 05의 `VERSION_CONFLICT` 대신 |
| U-2 | 발행 설정 창 태그 자동완성은 013 | 칩 입력만 |
| U-3 | `post_image`·640 썸네일은 008 | 첫 원본 주소 |

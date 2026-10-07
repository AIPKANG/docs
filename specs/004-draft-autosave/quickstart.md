# Quickstart: 임시저장·자동 저장 (004) 검증 가이드

**Phase 1 산출물** · 작성일 2026-10-07

경로·결과는 [contracts/web-routes.md](./contracts/web-routes.md), Service는 [contracts/post-edit-service.md](./contracts/post-edit-service.md), 데이터·오류 코드는 [data-model.md](./data-model.md).

## 1. 준비

003 quickstart §1과 같다(compose: PostgreSQL·Redis·Mailpit·저장소).

```bash
DB_PORT=55432 REDIS_PORT=56379 MAIL_PORT=51025 MAILPIT_WEB_PORT=58025 STORAGE_PORT=59000 docker compose up -d
DB_PORT=55432 REDIS_PORT=56379 MAIL_PORT=51025 STORAGE_PORT=59000 ./gradlew bootRun
./gradlew test                 # 통합 테스트(Testcontainers)
node src/test/js/diff.test.mjs # 비교 창 diff 순수 함수 자체 검사(선택)
```

## 2. 시나리오

### S1. 자동 저장과 이어 쓰기 (US1, SC-001, SC-003)
1. 인증된 회원으로 로그인 → 머리글 [새 글] → `/write/{id}`, DB `post` 행 `DRAFT`, `edit_version=0`, 공개 범위 = 설정의 기본값.
2. 제목·본문 입력 → 1초 뒤 "● 이 기기에 저장됨 (동기화 대기)", 3초 뒤 "✓ 저장됨 HH:MM". Redis `HGETALL autosave:post:{id}` 확인.
3. 1분 뒤 `post.title`/`content_md`/`edit_version` 반영. Redis `FLUSHDB` 후 다른 브라우저로 `/manage/posts` → 열기 → 같은 내용.
4. [저장] → 즉시 DB 반영. 아무것도 안 바꾸고 기다리면 네트워크 탭에 요청 없음.

### S2. 오프라인 (US2, SC-002)
1. 개발자 도구 Offline → 입력 → "⚠ 오프라인 — …". 떠나려 하면 확인창.
2. Online → 자동 동기화 → "✓ 저장됨".
3. Offline에서 입력 후 탭 닫기 → 다시 열기 → "이 기기에 저장되지 않은 변경을 불러왔어요".

### S3. 두 탭 충돌 (US3, SC-004, SC-006)
1. 같은 글을 탭 A·B로 연다. A 입력·저장 → B 입력 → B에 배너, 편집 계속 가능.
2. [비교하기] → 바뀐 줄·단어에 `−`/`+`와 색. 좁은 창은 위아래 보기.
3. 세 선택지 각각: 확인 문구 후 덮어씀 / 서버 내용으로 바뀌고 백업 안내 / 새 임시글 생성(원래 글 그대로). 닫기 → 배너 유지.
4. 자동: `ConcurrentAutosaveIT` — 같은 출발 버전 20건 중 성공 1건.

### S4. 발행 글 작업본 (US4, SC-005)
1. (발행은 005) SQL로 글을 `PUBLISHED`로 만든 뒤 편집·저장 → `post_draft`에만 반영, 비로그인 화면·`post` 내용 그대로.
2. `/manage/posts`에 "수정 중". [변경 취소] → `post_draft`·Redis 키 없음, `post` 그대로.

### S5. 빈 임시글 정리 (US5, SC-009)
- `EmptyDraftCleanupIT`: 빈 글 24시간 경과 → 삭제. 제목만 있음/본문만 있음/버퍼 있음/23시간 → 남음.

### S6. 권한·제한 (SC-008)
- `AutosavePermissionIT`: 비회원 401, 인증 전 403, 남의 글·없는 글·휴지통 404(내용·버전 그대로), 6초 안 두 번째 자동 저장 429, 1MB 초과 413.

### S7. Redis 장애 (SC-007)
- `AutosaveRedisDownIT`: 버퍼가 실패하면 DB에 바로 저장되고 버전 확인도 된다.

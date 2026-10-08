# Research: 내 글 관리·휴지통 (011-manage-posts-trash)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/41-manage-posts.md`, `docs/13-delete-withdraw.md` §1·§2·§4·§5, 004~010 구현

사용자 지시: 질문 없이 기본값. 004가 만든 최소 "내 글" 목록(`/manage/posts`)을 세 탭 화면으로 바꾼다.

## R-1. 목록 (FR-001~FR-012)
- **Decision**: `GET /manage/posts?tab=drafts|published|trash&visibility=public|private&cursor=` (기본 drafts, 비회원은 로그인 화면으로 돌아오기). `PostManageQuery.page(user, tab, visibility, cursor)`: 20개 보여 주려고 21개 조회, 본문 칸 제외, 작업본 유무는 `post_draft` LEFT JOIN으로 같은 쿼리(SQL 1번). 정렬·커서: 임시·발행 `(updated_at, id)`, 휴지통 `(deleted_at, id)` — 009 `FeedCursor` 형식 재사용. 탭 개수는 첫 화면에서 `GROUP BY` 1번. API `GET /api/me/posts`가 `{items, nextCursor, counts?}`. 관리자에게도 남의 관리 화면은 없다(경로에 회원 값이 없음).
- 줄 표시: 임시글 — 제목/"(제목 없음)", "마지막 저장 N분 전"(009 `CardDates`), [이어 쓰기]·[삭제]. 발행 글 — 🌐/🔒(+화면 낭독기 "공개"/"비공개"), [수정 중], 발행일·"수정됨", 조회·좋아요·댓글, 숨김 배지(022 `hidden_at`), [보기]·[수정]/[이어서 수정]+[변경 취소]·[공개 범위]·[삭제]. 휴지통 — 제목, "(임시글이었음)/(발행 글이었음)", 삭제일, "N일 뒤 완전 삭제", [복구]·[영구 삭제]; 위에 30일 안내.

## R-2. 삭제·복구·영구 삭제 (FR-016~FR-027, FR-032)
- **Decision**: `PostTrashService`(post 모듈). 권한: `AccountGuard.requireLoggedIn`(인증 전 회원도 허용, 42 §5-2) + 작성자 조건(남의 글 404).
  - 삭제 `DELETE /api/posts/{id}`: 먼저 버퍼를 DB에 반영(004 `AutosaveFlusher.flushOne`, FR-023) → 트랜잭션: `SELECT … FOR UPDATE`(작성자) → 이미 휴지통이면 그대로 200(FR-025) → 제목·본문이 공백뿐인 임시글이면 사진 연결 정리 후 즉시 `DELETE`(200 `{purged:true}`, FR-019) → 아니면 `deleted_at = now`(상태·공개 범위 그대로, FR-020) → 커밋 후 버퍼 삭제. 응답 `{trashed:true, purgeAt}`.
  - 복구 `POST /api/posts/{id}/restore`: 휴지통 글만(아니면 404), `deleted_at = NULL`(첫 공개 시각 그대로 → 목록 원래 위치).
  - 영구 삭제 `DELETE /api/posts/{id}/permanent`: 휴지통 글만(아니면 404) → 사진 연결 해제(`PostImageService.detachAll`: 다른 글이 안 쓰면 끊긴 시각 기록, FR-030) → `DELETE FROM post`(CASCADE: 댓글·좋아요·태그 연결·사진 연결·작업본·일별 조회·알림, 태그 자체는 남음, FR-029). 글 번호는 IDENTITY라 재사용되지 않는다(FR-031).
  - 같은 글 동시 요청은 행 잠금으로 순서대로(FR-032).
- 휴지통 글은 006 판정·목록 조건에서 이미 빠지고, 004·005·006의 저장·발행·공개 범위 변경도 휴지통이면 404다(FR-021, FR-024 — 테스트로 다시 확인).

## R-3. 30일 자동 정리 (FR-028)
- **Decision**: `TrashPurgeJob` 매일 05:20(Asia/Seoul, `blog.post.trash.purge-cron`), 004 `JobLock`, `deleted_at < now - 30d`인 글을 100개씩 같은 영구 삭제 경로로. 설정 `blog.post.trash.retention: 30d`, `purge-batch-size: 100`.

## R-4. 스크립트 없는 환경 (FR-013)
- **Decision**: 각 줄 버튼은 `<form method="post" action="/manage/posts/{id}/trash|restore|purge?tab=…">` 폼이고, JS(`manage.js`)가 가로채 API를 부른 뒤 그 줄만 바꾸거나 지운다. 실패하면 줄 아래 이유. 폼 제출은 같은 탭으로 303.

## R-5. 테스트
- 탭별 목록·정렬·20개·커서·개수·필터·본문 미조회, 삭제(빈 임시글 즉시·버퍼 반영 후 이동·멱등)·복구(원래 위치·반응 그대로)·영구 삭제(CASCADE·태그 남음·사진 끊김)·자동 정리(30일·잠금), 권한(남의 글 404·인증 전 허용·비회원 401), 휴지통 글에 대한 저장·발행·공개 범위 404, 동시 요청.

## 남은 확인 사항
| # | 내용 | 기본값 |
|---|---|---|
| U-1 | "수정 중" 배지는 작업본 행 기준(버퍼만 있는 경우 제외) | 41 §5 그대로 |

## 구현 메모 (/speckit-implement, 2026-10-08)
- **I-1.** 004의 최소 "내 글" 목록을 세 탭 화면으로 바꿨다. 004·006 테스트의 `/manage/posts` 기대값을 탭에 맞게 고쳤다(발행 글은 `?tab=published`).
- **I-2.** "수정 중" 배지는 작업본 행 기준이다(41 §5). 버퍼에만 있는 수정은 1분 안에 작업본으로 반영되면 표시된다.
- **I-3.** 완전 삭제는 `DELETE FROM post` + FK CASCADE이고, 그 전에 008 `PostImageService.detachAll`로 이 글에서만 쓰던 사진의 끊긴 시각을 기록한다(7일 뒤 사진 정리). 신고 사건(`report_case.post_id`)은 SET NULL로 남는다.
- **I-4.** 전체 Gradle 테스트 521개 통과.

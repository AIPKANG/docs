# Research: 공개 범위 (006-post-visibility)

**Phase 0** · 2026-10-07 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/06-visibility.md`, `docs/42-permission-matrix.md` §3·§5, 005 구현(`PostAccessPolicy`)

사용자 지시: 질문 없이 기본값. 공통 범위는 `PUBLIC`/`PRIVATE`. 친구 공개(US4)는 **선택 구현 규격이라 이번에 구현하지 않는다**(V1 `ck_post_visibility`도 두 값뿐). 대신 규칙 Bean 구조로 나중에 추가만 하면 되게 만든다.

## R-1. 읽기 판정 한 곳 + 규칙 Bean (FR-001~FR-005, FR-013)
- **Decision**: `post.application.visibility.VisibilityRule`(`visibility()`, `canRead(viewer, PostFacts)`, `listCondition(alias)` — 비작성자 공개 목록에 들어가는 조건, 없으면 `null`) 인터페이스와 `PublicVisibilityRule`·`PrivateVisibilityRule` Bean. `PostAccessPolicy.canRead(viewer, PostFacts)`: 휴지통(조회에서 제외) → 작성자가 탈퇴 유예·익명이면 아무도 못 봄(42 §5-1, 작성자는 복구 화면만) → 작성자 본인은 봄 → 임시글은 작성자만 → 규칙. 관리자 예외 없음. 숨김(022)은 여기에 단계를 더한다.
- **Rationale**: 06 §7 R-1·R-3, 헌법 III "공개 범위 확장은 규칙 추가".

## R-2. 공용 목록 조건 (FR-009~FR-011)
- **Decision**: `PostAccessPolicy.publicListingCondition("p", "m")` = `p.status='PUBLISHED' AND p.deleted_at IS NULL AND m.withdrawn_at IS NULL AND (규칙들의 listCondition OR …)`. 지금 규칙으로는 `p.visibility = 'PUBLIC'`. 홈·블로그·태그·검색·sitemap(009·013·020)은 이 조건만 쓴다. 작성자 본인의 블로그 목록도 같은 조건(FR-010). 이 기능에서는 조건을 만들고 테스트로 고정한다(목록 화면은 009).

## R-3. 404 통일 (FR-006~FR-008)
- **Decision**: 상세는 `NotFoundException` 하나. 공통 404 화면 `<head>`에 `og:title` "볼 수 없는 글이에요", `og:description` "친구 공개·비공개 글이거나 삭제된 글입니다.", `robots noindex`(레이아웃의 `notFoundPage` 표시로). 캐시: Spring Security 기본 헤더가 모든 응답에 `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`를 이미 붙인다 — 공유 캐시 저장 금지 충족, 테스트로 고정.

## R-4. 공개 범위 바꾸기 (FR-014~FR-021)
- **Decision**: `PATCH /api/posts/{postId}/visibility {visibility}` → 200 `{visibility, firstPublicAt}`. `PostVisibilityService.change`: 권한(401/403) → 값 검사(400 `INVALID_VISIBILITY`, 규칙 Bean에 없는 값) → 트랜잭션: `SELECT … FOR UPDATE`(작성자·휴지통 조건, 없으면 404) → 같은 값이면 그대로 200 → `UPDATE post SET visibility, first_public_at = CASE …(06 §4), updated_at`(작업본·`edit_version`·`edited_at` 그대로) → 사건 `PostVisibilityChanged(postId, from, to)`(AFTER_COMMIT 구독). 임시글도 값만 저장.
- 판정 순서 42 §3에 맞춰 404(대상)를 400(값)보다 먼저 본다.

## R-5. 화면
- **Decision**: 내 글 목록에 배지(🌐 전체 공개 / 🔒 나만 보기)와 발행 글의 [나만 보기로]/[전체 공개로] 버튼(JS PATCH, 성공하면 배지 갱신). 편집 화면 발행 설정은 005 그대로. 설정 화면 기본 공개 범위는 003 그대로(FR-022·023은 003·004에서 끝남 — 테스트로 다시 확인).

## R-6. 테스트
- 접근 표: 공개 범위 × 보는 사람(비회원·다른 회원·작성자·관리자) × 상태(임시·발행·휴지통·탈퇴 유예 작성자) → 상세 결과, 404 본문·OG·noindex·캐시 헤더가 없는 글과 같음. 변경: 즉시 적용·`edited_at`/작업본/버전 불변·`first_public_at` 규칙·같은 값 200·권한 404/401/403·400·동시 변경 20건. 목록 조건: 조건 SQL로 직접 조회해 PUBLIC·발행·휴지통 밖·탈퇴 아닌 작성자 글만.

## 남은 확인 사항
| # | 내용 | 기본값 |
|---|---|---|
| U-1 | 친구 공개 규격(US4·FR-024~029) | 구현 안 함(선택). `FRIENDS`는 400 |
| U-2 | 댓글·좋아요 숨김(FR-012·017) | 014·015가 `PostAccessPolicy`로 판정 |

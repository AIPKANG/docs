---
description: "006-post-visibility 구현 작업 목록"
---
# Tasks: 공개 범위

**작업 ID**: 006은 **`T601`부터**.

- [ ] T601 [P] `VisibilityRule`·`PublicVisibilityRule`·`PrivateVisibilityRule`·`PostFacts`
- [ ] T602 `PostAccessPolicy` 재작성(탈퇴 유예 작성자·임시글·규칙), `publicListingCondition`, `PostDetailQuery`가 `PostFacts` 사용
- [ ] T603 [P] [US1] `PostAccessMatrixIT`: 공개 범위 × 보는 사람 × 상태 상세 결과, 404 본문·OG·noindex·캐시 헤더 동일
- [ ] T604 [US1] 공통 404 화면 OG·noindex(`notFoundPage`)
- [ ] T605 [P] [US1] `PublicListingConditionIT`: 조건 SQL이 공개·발행·휴지통 밖·탈퇴 아닌 작성자 글만
- [ ] T606 [P] [US2] `VisibilityChangeIT`: 즉시 적용, `edited_at`·작업본·버전 불변, `first_public_at` 규칙, 같은 값 200, 404/401/403/400, 동시 20건, 임시글 값만
- [ ] T607 [US2] `PostVisibilityService`, `PostVisibilityApiController`, `PostVisibilityChanged`
- [ ] T608 [US2] 내 글 목록 배지·[나만 보기로]/[전체 공개로](`manage.js`)
- [ ] T609 [US3] 기본 공개 범위 확인(003·004 테스트로 충족, `DraftCreateIT` 재확인)
- [ ] T610 전체 테스트·구현 메모

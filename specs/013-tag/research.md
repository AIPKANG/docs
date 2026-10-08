# Research: 태그 (013-tag)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), `docs/22-tag.md`, 005(정규화·발행 때 저장)·006·009 구현

사용자 지시: 질문 없이 기본값. 005가 만든 `TagNormalizer`·`PostTagService`를 쓰고 읽기 화면을 더한다.

## R-1. 정리 규칙 하나 (FR-001~FR-007)
- `TagNormalizer`(005): 발행 검사(금칙어 포함), `lookupName`(주소·필터·자동완성, 금칙어 제외), `shape`(화면 안내용 JS와 같은 규칙). 발행 오류 항목에 `value`(입력값)를 담는다(FR-007) — `FieldError.withValue`.

## R-2. 읽기 쿼리 위치
- 태그별 글 수·목록·자동완성은 "공개 글"을 세야 하므로 006 공용 조건을 가진 post 모듈의 `TagListingQuery`에 둔다(태그 테이블은 읽기만). 태그별 목록은 009 `PostListQuery`에 `EXISTS post_tag` 조건만 더한다(같은 카드·커서·9개).

## R-3. 주소 (FR-016~FR-018)
- `UriUtils.encodePathSegment`(`#` → `%23`, `+`·`.` 그대로). `lookupName` 결과가 원래 값과 다르면 301, 비면 404. 공개 글 없는 태그는 200 + 같은 빈 화면(없는 태그와 구별 없음). `StrictHttpFirewall` 기본값으로 `%23`·한글 인코딩 통과 확인(테스트).

## R-4. 전체 태그 (FR-019·FR-020)
- 상위 100개 계산 결과를 Redis `tags:top`에 10분 캐시, 없으면 바로 계산.

## R-5. 자동완성 (FR-021~FR-024)
- `GET /api/tags/suggest?q=` 로그인, 1분 60번, `shape` 후 앞부분 일치(`LIKE … ESCAPE`), 후보 = 내 태그 ∪ 공개 글 수 ≥ 1, 정렬 내 태그 → 공개 글 수 → 이름, 10개. 화면은 0.3초 대기·한글 조합 중 호출 안 함.

## R-6. 블로그 태그 (FR-025·FR-026)
- 블로그 머리말 아래 태그 줄(10개 + `<details>` 태그 더 보기), `/@주소?tag=` 필터(정리 안 된 값 301), API `/api/members/{handle}/tags`, `/api/members/{handle}/posts?tag=`.

## R-7. 입력 화면 (FR-008~FR-010)
- 005 발행 설정 칩에 끌어서 순서 바꾸기·`Alt`+방향키, 자동완성 목록을 더했다.

## 구현 메모 (2026-10-08)
- 테스트: `TagPagesIT` 6개(공개 조건·주소 왕복 6종·301·404·빈 태그 동일 화면·상위 정렬·캐시·자동완성 후보·블로그 필터·동시 발행 같은 새 태그 1개·오류 값). 전체 531개 통과.

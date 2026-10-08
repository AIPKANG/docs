---
description: "010-post-detail 구현 작업 목록"
---
# Tasks: 글 상세
**작업 ID**: 010은 **`T1001`부터**.
- [X] T1001 [P] `ViewCountFormat`(1,234 / 1.2만) + 단위 테스트
- [X] T1002 `PostDetailQuery` 재작성(글+작성자 JOIN 1번, 태그, 작업본 정보), `PostDetail`
- [X] T1003 `PostDetailController` 주소 처리 순서·캐시 지시·메타 값
- [X] T1004 `detail.html` 재구성(배지·버튼·날짜·수정됨·태그 링크·반응 줄·작성자 카드·수정 중 안내·OG·canonical·noindex), `og-default.png`
- [X] T1005 `post-detail.js`(공개 범위 즉시 변경, 변경 취소), 기존 005 `PostDetailIT` 갱신
- [X] T1006 [P] `PostDetailPageIT`: 주소 처리 6단계, 404 동일, 메타·캐시, 날짜 규칙, 버튼 노출, 쿼리 수
- [X] T1007 전체 테스트·구현 메모

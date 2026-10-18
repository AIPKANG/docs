# Feature Specification: 내 글 쪽 번호

**Feature Branch**: `113-manage-pages` | **Created**: 2026-10-16 | **Status**: Implemented (blog 1.66.5)

**Scope**: **강성찬 개인 확장**.

## 바꾸기 전
- 내 글 목록은 [더 보기]로 다음 묶음을 아래에 계속 이어 붙였다.

## 결정
- 한 쪽에 20개, 목록 아래에 쪽 번호(‹ 1 2 … ›). 주소는 `?page=2`.
- 숫자가 아니면 1쪽, 끝 쪽보다 크면 끝 쪽을 보여 준다.
- 글을 지우거나 옮겨서 이 쪽에 남은 줄이 없으면 앞 쪽으로 간다(1쪽이면 새로 불러 뒤 글이 올라온다).
- DB 바뀜 없음.

## Success Criteria
- 통합 테스트(ManagePostsIT): 45개에서 1쪽 20개와 3쪽까지 번호, 2쪽 내용, page=9는 끝 쪽(3쪽), page=x는 1쪽, [더 보기] 없음.

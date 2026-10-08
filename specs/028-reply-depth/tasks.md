# Tasks: 답글 무제한 깊이 — 강성찬 개인 확장

- [x] T001 CommentProperties.maxDepth(공통 1, 0 = 무제한), application.yml 0, 테스트 설정 1
- [x] T002 CommentService.create 깊이 모드(바로 위 댓글, 깊이 검사), delete 일반화(자리 남기기·위로 정리)
- [x] T003 InteractionPurgeSteps.Comments 일반화(잎만 삭제, 빈 자리 위로 정리)
- [x] T004 CommentQuery 깊이 모드: 페이지 대화 전체 나무(재귀 쿼리 1번), around는 위로 끝까지 최상위
- [x] T005 화면: thread·node 재귀 조각, 3단계 이후 접기, 각 단계 처음 3개, 날짜 재귀, 접힌 대상 펼치기, 들여쓰기 상한
- [x] T006 NestedRepliesIT(구조·알림·접기·3개 규칙·삭제 정리·탈퇴 정리), 전체 테스트, CHANGELOG

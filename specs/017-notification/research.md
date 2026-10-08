# Research: 인앱 알림 (017-notification)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/25-notification.md`, `docs/20-domain-events.md`, `docs/42-permission-matrix.md` §10-3

사용자 지시: 질문 없이 기본값.

## R-1. 사건 처리 (FR-001~FR-008)
- `@TransactionalEventListener(AFTER_COMMIT)` → `NotificationDispatcher`(스레드 1개, 대기열 1,000, 가득 차면 버리고 경고, 예외는 기록만). 스레드가 하나라 같은 글의 좋아요·취소 순서가 지켜진다. 테스트는 `async: false`로 바로 처리.
- 사건에 식별자만 있어(FR-004) 받는 사람·글 상태는 처리 때 다시 읽는다. 답글 받는 사람을 알려고 `CommentCreated`에 `replyTargetAuthorId`(답한 댓글의 작성자)를, 처음 공개를 알려고 `PostVisibilityChanged`에 `firstPublic`을 더했다(필드 추가만).
- 새 글: 공개로 발행(`PostPublished` + `PUBLIC`)이나 처음 공개로 바뀜(`firstPublic`) 때 한 번. 처리 시점에 공개·발행·숨김 아님을 다시 보고 팔로워에게 한 문장 INSERT…SELECT.
- 팔로우(018)·신고 결과·숨김(022) 사건은 아직 없어 `NotificationWriter.addToGroup(FOLLOW)`·`operational(...)`을 공개해 두고 그 기능이 리스너를 더한다. 탈퇴 정리 단계 `NotificationCleanup.purgeWithdrawn`은 023이 익명 처리의 글·댓글 단계 다음에 부른다.

## R-2. 묶음 (FR-009~FR-013)
- 25 §4-2 그대로: 이미 들어간 적 확인(좋아요는 기간 무관, 팔로우는 7일) → `ON CONFLICT … WHERE read_at IS NULL` 묶음 찾기/만들기 → `notification_actor` `ON CONFLICT DO NOTHING` → 새로 들어왔으면 인원+1·대표·갱신 시각. 취소는 안 읽은 묶음에서만 빼고 다시 계산, 0명이면 삭제.
- 안 읽은 묶음에서 빠진 사람이 다시 누르면 다시 들어간다(인원은 그대로라 SC-002 만족).

## R-3. 보여주기 (FR-017~FR-030)
- 목록 쿼리 1번(알림 + 마지막 행동자 + 글·작성자 + 댓글 LEFT JOIN), 읽기 판정은 `PostAccessPolicy.canRead` + 휴지통·숨김을 메모리에서. 문장(`message`)은 서버가 만들고 화면은 글자로만 넣는다.
- 글 숨김 알림은 작성자 본인이라 숨겨져도 제목, 댓글 숨김 알림은 글 제목 없음, 신고 결과는 이동 없음, 운영 알림에 행동한 사람 없음.
- 새 팔로워 이동 위치는 `/me/followers`(018이 만든다).
- 종 배지는 SSR에서 그리지 않고 스크립트가 열 때 바로 한 번 확인한다(모든 화면에 쿼리를 더하지 않으려고 — 25 §5 "SSR 배지"와 다른 점, U-1).

## R-4. 정리 (FR-032, FR-034)
- 매일 04:30 `JobLock`: 90일 지난 알림 1,000개씩, 받는 사람마다 최신 1,000개 밖 삭제.

## 남은 확인
- U-1: 종 배지를 서버가 처음부터 그릴지(지금은 스크립트가 열자마자 확인).

## 구현 메모 (2026-10-08)
- `NotificationIT` 7개(받는 사람·본인 제외·삭제, 읽음·커서·소유·폼, 동시 좋아요 10건 묶음·취소·재클릭, 팔로우 7일, 새 글 한 번·끄기·탈퇴·지금 상태, 운영 알림·설정 화면, 정리·탈퇴 정리), `NotificationFailureIT`(알림 실패에도 댓글·좋아요 성공), 매트릭스 §10-3. 테스트 고정 글이 이미 `first_public_at`을 갖고 있어 "처음 공개" 시나리오는 그 값을 비우고 시작한다.

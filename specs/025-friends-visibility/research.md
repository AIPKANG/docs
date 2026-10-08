# Research: 친구 공개 — 강성찬 개인 확장

- **R-1 규칙 Bean 하나 추가**: `FriendsVisibilityRule.canRead` = 보는 사람이 로그인했고 글쓴이와 수락된 친구. `listCondition` = null(어떤 공용 목록에도 넣지 않음). 06 §7 R-3 그대로.
- **R-2 친구가 보는 블로그 목록 정렬**: 06 §6-3은 `published_at DESC`. 공개 글의 자리가 친구냐 아니냐에 따라 달라지지 않도록 `COALESCE(first_public_at, published_at) DESC, id DESC`로 정했다(공개 글은 최초 공개 시각, 친구 공개 글은 발행 시각). 카드 날짜도 같은 값.
- **R-3 맞요청**: 한 쌍에 행 하나(`(작은 id, 큰 id)` PK). 요청은 `INSERT … ON CONFLICT DO NOTHING` 후, 이미 상대가 보낸 요청(PENDING, requested_by=상대)이면 바로 ACCEPTED로 바꾼다. 같은 트랜잭션에서 행을 `FOR UPDATE`로 잠가 동시 요청 20번에도 한 행.
- **R-4 알림**: `FRIEND_REQUEST`를 팔로우처럼 묶음 알림으로("OO님 외 N명이 친구 요청을 보냈어요" → 설정 친구 화면). 수락·거절·취소·끊기는 받은 사람의 안 읽은 묶음에서 그 사람을 뺄 뿐 아무에게도 새 알림을 만들지 않는다. 끌 수 없는 종류로 둔다(요청은 처리해야 하는 일이라).
- **R-5 요청 수 제한**: 하루 50건(설정 `blog.friend.daily-request-limit`), 기존 Redis 요청 제한기 사용. 넘으면 429 `RATE_LIMITED`.
- **R-6 탈퇴 정리**: `WithdrawalPurgeStep` order 55(팔로우 60 앞, 13 §3-3 "친구 관계 삭제").
- **R-7 상대 상태**: 탈퇴 유예·익명·정지된 회원에게는 요청 불가(404). 정지된 회원의 친구 공개 글은 공통 규칙대로(정지는 쓰기만 막음).
- **R-8 공통 테스트 영향**: 공개 범위 값 검사 테스트 중 "FRIENDS는 400"을 기대하던 것은 이 확장으로 바뀐다(개인 확장이 의도한 변경).

# Source notes: 017-notification

원문: `docs/25-notification.md`, `docs/20-domain-events.md` (참고: `docs/42-permission-matrix.md` §10-3, `docs/43-report-hide.md` H-10·§이벤트 표, `docs/44-withdraw.md` 정리 단계 표)

## plan 단계에서 참고할 기술 결정

### 도메인 이벤트 (20)
- 전달: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("eventExecutor")` + 리스너에서 `REQUIRES_NEW` (20 EV-1, §2-2)
- `eventExecutor`: 코어 2, 최대 4, 대기열 1,000, 가득 차면 버리고 경고 로그 (20 §2-2)
- outbox·Kafka 없음, 유실 허용 (20 EV-2, §6). 김민서 `outbox_event`는 개인 확장
- 이벤트는 Java `record`, `shared/event` 패키지, 이름 `{대상}{과거분사}`, 필드는 ID·enum·`Instant`만 (20 EV-3, EV-7, §2-1)
- 발행은 Service에서만, 상태 변경 확인 후 (`ON CONFLICT DO NOTHING`/`RETURNING`으로 판단) (20 EV-4, §2-1)
- 이벤트 목록과 필드: 글 7종 `PostPublished`·`PostEdited`·`PostVisibilityChanged`·`PostWentPublic`·`PostTrashed`·`PostRestored`·`PostPurged` (20 §3-1), `CommentCreated`·`CommentDeleted` (§3-2), `PostLiked`·`PostUnliked` (§3-3), `MemberFollowed`·`MemberUnfollowed` (§3-4), `ReportResolved`·`ContentHidden`·`ContentUnhidden` (§3-5), `MemberWithdrawn`·`MemberRestored` (§3-6), 친구 규격 `FriendRequested`·`FriendAccepted` (§3-7)
- `PostWentPublic`은 엔티티 메서드가 `first_public_at` 최초 기록 여부를 반환 → 같은 트랜잭션에서 함께 발행 (20 §3-1)
- 이벤트 → 알림 매핑과 받는 사람 결정 표 (20 §4, §4-1), 취소·삭제 이벤트 영향 (20 §4-2), 구독처 표 (20 §5)
- 완료 기준 테스트: `@RecordApplicationEvents`, record 필드 검사(ArchUnit/리플렉션) (20 §7)
- 43이 `MemberSuspended(memberId, until, reason)`를 20 목록에 추가 요청 (알림 없음) (43 이벤트 표)

### 알림 (25)
- 테이블 `notification`(대상별 nullable FK `post_id`·`comment_id` `ON DELETE CASCADE`, `report_id`는 report 확정 후 FK, `group_key`, `last_actor_id`, `actor_count`, CHECK 4개), `notification_actor`(PK `(notification_id, actor_id)`), `notification_mute`(PK `(member_id, type)`) — V2 마이그레이션 (25 §10, NT-6·NT-7). 03 §5의 `target_type`/`target_id` 대체
- 인덱스: 부분 유일 `uq_notification_unread_group (receiver_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL` 외 5개 (25 §10)
- 생성 전 확인 ①~⑥, 읽기 판정은 `PostAccessPolicy.canRead` (25 §4)
- `NEW_POST`는 `INSERT … SELECT` 한 문장으로 팔로워 전원 (25 §4-1)
- 묶음 처리 4단계: 중복 확인 → `INSERT … ON CONFLICT … DO UPDATE … RETURNING id` → `notification_actor` `ON CONFLICT DO NOTHING` → count+1 (25 §4-2)
- 취소·삭제 이벤트 처리 표 (25 §4-3)
- API: `GET /api/notifications/unread-count`(`Cache-Control: no-store`), `GET /api/notifications?cursor&size`, `PATCH /api/notifications/{id}/read`, `POST /api/notifications/read-all`, `DELETE /api/notifications/{id}`, `GET|PUT /api/me/notification-settings`, 응답 JSON 예시 (25 §5, §7)
- 커서 `(updated_at, id)`, 브라우저가 이미 있는 ID 건너뜀 (25 §5, 10 §4-3)
- 목록 쿼리 1번(JOIN) + 권한 판정 일괄 (25 §5)
- 댓글 링크 `/@주소/posts/{글}?comment={id}#comment-{id}`, canonical 제외, 위치 로딩은 21 §6 `around` (25 §2, §12)
- 정리 배치: 매일 새벽 1,000개씩 삭제, ShedLock (25 §6)
- 탈퇴 정리: `NotificationWithdrawalPurgeStep`, 순서 70(글·댓글 단계 다음) (25 §8, 44 정리 단계 표, 20 §10 `WithdrawalPurgeStep` 인터페이스)
- 화면 와이어프레임 (25 §3, §7)

## 문서 간 차이 (확인 필요)
- 숨김 알림 문구: 25 §2에는 사유가 없고 43 H-10은 "운영 정책에 따라 숨겨졌어요(사유)" — 사유는 `hidden_reason`에서 읽는다 (43 이벤트 표)
- 20 §5는 트렌딩이 이벤트를 구독할 것으로 예상했으나 32는 10분 주기 재계산이라 구독하지 않음

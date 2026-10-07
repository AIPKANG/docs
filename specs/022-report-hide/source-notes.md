# Source Notes: 022-report-hide

원문: `docs/43-report-hide.md` (상태: 초안). spec.md에서 뺀 구현 세부를 `/speckit-plan`용으로 남긴다.

## plan 단계에서 참고할 기술 결정

- 모듈: `moderation` (나민서) (20 §3-5)
- API: `POST /api/reports {targetType, targetId, reason, detail}` — 200(중복도 200) / 400 `CANNOT_REPORT_OWN`·`REPORT_DETAIL_REQUIRED` / 401 / 403 `EMAIL_NOT_VERIFIED` / 404 / 429 (43 §2)
- 화면 주소: `/admin/reports`, `/admin/members/{handle}` — 일반 회원 404 (43 §3, §6, 42 P-10)
- 사유 코드: `SPAM`, `ABUSE`, `SEXUAL`, `PRIVACY`, `COPYRIGHT`, `OTHER`; 상태: `PENDING`, `HIDDEN`, `REJECTED`, `CLOSED_NO_TARGET` (43 ERD)
- `report` 테이블 DDL, `UNIQUE(reporter_id, target_type, target_id)` + `ON CONFLICT DO NOTHING`, CHECK(self·detail·reason·status), 부분 인덱스 `ix_report_pending` (43 §2, ERD 변경 제안)
- `post`·`comment`에 `hidden_at`, `hidden_by`, `hidden_reason`; `member`에 `suspended_until`(영구=null), `suspended_reason`; `status = SUSPENDED`는 기존 (43 ERD)
- 06 §7 R-2a 공용 조건(`VisibilityFilter`)과 `PostAccessPolicy.canRead`에 `hidden_at IS NULL`(작성자 제외) 추가, `ix_post_feed`·`ix_post_blog` WHERE에 `hidden_at IS NULL` (43 "기존 결정 변경") — 전원 동의는 화요일 회의
- 신고 남용 방지: Redis 카운터 1분 5건·하루 50건 → 429 (43 H-13)
- 정지: 세션 전부 삭제(Redis), 로그인 시 `suspended_until` 경과면 `ACTIVE`로 (배치 없음), 로그인 실패 코드 `ACCOUNT_SUSPENDED` (43 §6, 42 §4)
- 이벤트(커밋 후 발행): `ReportResolved(reportId, reporterId, targetType, targetId, result, resolvedAt)` 신고마다 1개, `result` = `ACTION_TAKEN`/`NO_VIOLATION`; `ContentHidden(targetType, targetId, ownerId, postId, hiddenAt)` 신고자 ID 없음; `ContentUnhidden`; `MemberSuspended(memberId, until, reason)` (43 §7, 20 §3-5)
- 알림 종류: `REPORT_RESOLVED`, `CONTENT_HIDDEN` (25), `notification.report_id` FK `ON DELETE SET NULL` 제안 (43 ERD)
- 탈퇴 연동: `ReportWithdrawalPurgeStep` order 80 → `CLOSED_NO_TARGET` (44 §4)

## 문서 간 차이 / 미결

- 20 §3-5 `ReportResolved.targetType`에 `MEMBER` 포함 vs 43 H-1(글·댓글만) → spec은 글·댓글만. 20 수정 요청 상태
- `MemberSuspended` 이벤트가 20 목록에 아직 없음 (43 "요청")
- 21 ERD는 `comment.hidden_at`만 → 43이 3개 컬럼으로 확정 (요청)
- 32 트렌딩 T-4 "작성자 외 댓글 작성자 수"에서 숨긴 댓글 제외 (요청)
- 43 §8 완료 기준 번호가 1~6, 11, 12, 7~10 순으로 섞여 있음 (내용 문제 없음)

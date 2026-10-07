# Source Notes: 023-account-withdraw

원문: `docs/44-withdraw.md` (상태: 초안), `docs/13-delete-withdraw.md` §3·§4·§5. spec.md에서 뺀 구현 세부를 `/speckit-plan`용으로 남긴다.

## plan 단계에서 참고할 기술 결정

- 화면 주소: `/settings/withdraw`, 완료 `/withdrawn`, 복구 `/account/restore` (44 §2, §3, 11 §2)
- API: `POST /api/me/withdraw {password?, confirmText?}` — 400 `CURRENT_PASSWORD_MISMATCH`(5회 잠금) / 400 `CONFIRM_TEXT_MISMATCH` / 409 `ADMIN_CANNOT_WITHDRAW`; `POST /api/me/restore` (44 §2, §3)
- 유예 중 다른 요청: SSR 302 → 복구 화면 / REST 403 `ACCOUNT_WITHDRAWN` (44 §3, 42 P-12)
- 상태 컬럼: `member.status = WITHDRAWN`, `withdrawn_at`, `deleted_at`; CHECK `ck_member_withdrawn`, `ck_member_deleted`, `ck_member_nickname_null`; 부분 인덱스 `ix_member_withdraw_purge` (13 §4). 44는 ERD 변경 없음
- 닉네임 UNIQUE(`lower(nickname)`)가 NULL을 무시 → 익명화 즉시 닉네임 해제 (13 §4)
- 이벤트(커밋 후): `MemberWithdrawn(memberId, withdrawnAt)`, `MemberRestored(memberId, restoredAt)`. `MemberPurged`는 만들지 않음 (44 §2·§3, 20 §3-6)
- 30일 뒤 처리: `WithdrawPurgeJob` 매일 새벽 + ShedLock, 대상 `status = WITHDRAWN AND deleted_at IS NULL AND withdrawn_at < now() - 30일`, 회원 1명 1 `@Transactional` (44 §4, 13 §3-3)
- `WithdrawalPurgeStep { int order(); void purge(long memberId); }` 빈 목록을 order 순 실행, 10 단위 (44 §4):
  10 Post / 20 Comment / 30 Like / 40 Image / 50 AuthIdentity / 60 Friendship(적용자) / 65 Follow / 70 Notification / 80 Report / 90 Member, 커밋 후 Redis 세션·토큰·실패 횟수·요청 횟수 키 삭제
- 글 완전 삭제 SQL(사진 `detached_at`, CASCADE 대상) (13 §2-5)
- 익명화 대상 컬럼: `nickname`, `bio`, `profile_image_url`(11 결정 이후 `profile_image_id`), `nickname_changed_at`, `ai_consent_at`, `suspended_until`, `suspended_reason` = null, `deleted_at = now()`; `handle` 유지; `terms_agreed_at`·`privacy_agreed_at`·`created_at` 유지 (13 §3-3·§4, 44 §4)
- 브라우저 IndexedDB 임시 글 삭제는 로그아웃과 동일 (07 §7)
- 비밀번호 잠금 규칙 11 §6-2 재사용

## 문서 간 차이 / 미결

- 13 §3-3 7번은 `profile_image_url`을 비운다고 적었으나 11·01 결정 기록은 `member.profile_image_id` 사용 → 실제 컬럼 기준으로 정리
- 13 §3-3에는 AI 동의·정지 정보·알림·팔로우·신고 단계가 없음 → 44 §4(2026-10-06)가 추가, 44를 따름
- `order` 값(20·40·65·70) 강성찬 확인 요청, 30(좋아요) 김민서 확인 요청, Post·AuthIdentity·Member 단계 담당은 화요일 회의 (44 "다른 담당자와 맞출 것")

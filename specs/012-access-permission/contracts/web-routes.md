# Contract: 응답 규칙 (012)
401 `LOGIN_REQUIRED`(화면은 로그인으로 303) · 403 `EMAIL_NOT_VERIFIED`/`ACCOUNT_WITHDRAWN`(계정 상태만) · 404 `NOT_FOUND`(없음·볼 수 없음·내 것 아님·관리자 전용, 본문 동일) · 400 업무 규칙 · 409 상태 충돌. 판정 순서: 로그인 → 계정 상태 → 볼 수 있음 → 행동 권한 → 업무 규칙.

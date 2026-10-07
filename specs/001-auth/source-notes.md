# 001-auth 원문 참고 노트

## plan 단계에서 참고할 기술 결정

- 세션: Spring Session Data Redis, 14일(마지막 활동 기준). JWT 사용 팀원은 Refresh 토큰을 Redis에 저장·재발급마다 교체 — 07 §6, 헌법 기술 제약
- 쿠키 `HttpOnly`·`Secure`·`SameSite=Lax`, 세션 고정 방지·CSRF는 Spring Security 기본값 유지 — 07 §6
- 비밀번호 해시: BCrypt, `auth_identity.password_hash` — 07 §4
- 인증 토큰: 32바이트 난수 URL-safe 인코딩, Redis `auth:verify:{토큰}` TTL 24h / 재설정 `auth:reset:{토큰}` TTL 30분, 사용 시 삭제 — 07 §3, §4-1
- 재발송 카운터 Redis `auth:verify-resend:{memberId}:{yyyyMMdd}`; 실패 횟수·IP 제한도 Redis 카운터 — 07 §3, §6
- 스키마: `auth_identity.email_verified_at`, `UNIQUE(provider, provider_user_id)`, `UNIQUE(member_id)`, `LOCAL`은 `provider_user_id = email` CHECK, `ix_auth_identity_email`, `member.terms_agreed_at`·`privacy_agreed_at` — 07 §8, 03-erd.md
- provider 값 `LOCAL`/`GOOGLE`/`GITHUB` (네이버는 provider 값만 추가하면 확장) — 07 §2, L-5
- OAuth: state 검증, Google `sub`·`email_verified`, GitHub `user:email` 스코프로 인증된 primary 이메일 — 07 §5
- 소셜 인증 정보는 가입 마무리 전 세션에 10분 보관 — 07 §5
- 메일: 개발은 Mailpit, 운영은 Gmail SMTP + 앱 비밀번호 (2026-10-07 확정, research R-13) — 07 L-9
- 로그아웃 시 IndexedDB `draft:{memberId}:*`, `draft-backup:{memberId}:*` 삭제 — 07 §7, 04 §2-2
- 응답 코드·이유 코드(`LOGIN_REQUIRED`, `EMAIL_NOT_VERIFIED`, `ACCOUNT_WITHDRAWN`, `ACCOUNT_SUSPENDED`), 판정 순서 — 42 §3, §4
- 로그인 후 리다이렉트는 상대 경로만 (나민서 D-14) — 07 §6

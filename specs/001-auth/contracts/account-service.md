# Contract: account 모듈 공개 Service (다른 모듈·표현 계층용)

헌법 I: 다른 모듈은 account의 Repository·테이블을 직접 쓰지 않고 아래 공개 Service와 이벤트로만 소통한다. 시그니처는 설계 수준의 모양이며, 구현 코드는 tasks·implement 단계에서 만든다.

---

## 1. shared.security — 모든 쓰기 Service가 쓰는 것

| 타입 | 책임 | 비고 |
|---|---|---|
| `CurrentUser` (값 객체) | `memberId`, `role`. 인증 정보(SecurityContext)에서만 만든다 | 요청 값으로 회원 ID를 받지 않는다 (헌법 III) |
| `CurrentUserProvider` | 현재 요청의 `Optional<CurrentUser>` | SSR·REST 공통 |
| `AccountGuard.requireLoggedIn(currentUser?)` | 비회원이면 `LoginRequiredException` | 자기 글·댓글 삭제·복구 등 (42 §5-2) |
| `AccountGuard.requireWritable(currentUser?)` | 42 §3 ①② 순서: 비회원 → `LoginRequiredException`, 탈퇴 유예 → `AccountStatusException(ACCOUNT_WITHDRAWN)`, 인증 전 → `AccountStatusException(EMAIL_NOT_VERIFIED)` | 글 작성·저장·발행, 댓글 쓰기·수정, 사진 업로드, 좋아요, 신고, 프로필 사진 변경 |

예외 → 응답 매핑(`shared.error.GlobalExceptionHandler`)

| 예외 | SSR | REST |
|---|---|---|
| `LoginRequiredException` | 로그인 화면으로(돌아올 상대 경로 포함) | 401 `LOGIN_REQUIRED` |
| `AccountStatusException(EMAIL_NOT_VERIFIED)` | 403 안내 화면 + 재발송 버튼 | 403 `EMAIL_NOT_VERIFIED` |
| `AccountStatusException(ACCOUNT_WITHDRAWN)` | 복구 화면으로 | 403 `ACCOUNT_WITHDRAWN` |

## 2. account.application — 인증 기능 Service

| Service | 연산 | 결과·오류 | 트랜잭션 / 커밋 후 |
|---|---|---|---|
| `EmailSignupService` | `signUp(EmailSignupCommand)` → `memberId` | `DuplicateEmailException`(FR-007), `WithdrawnAccountExistsException`, `PasswordPolicyViolation`, `AgreementRequiredException`, 주소·닉네임 규칙 위반(002) | member + auth_identity(LOCAL, 미인증) + member_agreement×2 / 커밋 후 `VerificationMailRequested` |
| `EmailVerificationService` | `verify(token)` | `VERIFIED` / `EXPIRED_OR_USED` | 토큰 `GETDEL` → `email_verified_at` 기록 |
| | `resend(memberId)` | `SENT` / `RATE_LIMITED` / `ALREADY_VERIFIED` | 새 토큰, 이전 토큰 삭제 / 커밋 후 메일 |
| `SocialLoginService` | `resolve(SocialProfile)` → `ExistingMember(memberId)` 또는 `NewSignupRequired(PendingSocialSignup)` | — | 읽기 |
| | `findSameEmailAccounts(pending)` → 다른 수단 목록(provider만) | 인증된 이메일이 없으면 빈 목록 | 읽기 (FR-033) |
| `SocialSignupService` | `complete(PendingSocialSignup, SocialSignupCommand)` → `memberId` | `PendingExpiredException`(10분), 규칙 위반, 동시 완료 시 기존 `memberId` 반환 | member + auth_identity + member_agreement×2 |
| `LoginAttemptService` | `checkAllowed(emailNorm, ip)`, `recordFailure(emailNorm)`, `recordSuccess(emailNorm)` | `LoginLockedException` / `IpRateLimitedException` | Redis만 |
| `AccountStatusChecker` | `checkOnLogin(memberId)` → `ACTIVE` / `SUSPENDED(endsAt, reason)` / `WITHDRAWN_PENDING` | 기간 지난 정지 자동 해제 | 해제 시 쓰기 트랜잭션 |
| `PasswordResetService` | `request(email, ip)` | 항상 같은 결과(화면 분기 없음) | 커밋 후 `PasswordResetMailRequested`(LOCAL 링크 / 소셜 안내 / 아무것도 안 함) |
| | `reset(token, newPassword)` | `RESET` / `EXPIRED_OR_USED` / `PasswordPolicyViolation` | 해시 저장 → 커밋 후 `SessionRevoker.revokeAll(memberId)` |
| `SessionRevoker` | `revokeAll(memberId)` | 삭제한 세션 수 | Spring Session 인덱스로 principal = memberId 세션 전부 삭제. 43(정지)·003(비밀번호 변경)도 이 Service를 쓴다 |
| `PasswordPolicy` (domain) | `validate(rawPassword, email)` → 위반 목록 | 길이·조합·허용 문자·이메일 앞부분·흔한 비밀번호 | 순수 함수. 003 비밀번호 변경과 공유 |
| `HandleService`, `NicknamePolicy` | 주소 생성·검사, 닉네임 검사 | 규칙 소유는 002 | research R-17 |

## 3. 도메인 이벤트 (shared.event, 커밋 후 처리)

| 이벤트 | 발행 시점 | 구독(이 기능) | 다른 기능이 구독할 수 있음 |
|---|---|---|---|
| `MemberSignedUp(memberId, provider)` | 가입 커밋 후 | — | 알림·통계(개인 확장) |
| `VerificationMailRequested(memberId, email, rawToken)` | 가입·재발송 커밋 후 | 메일 발송 | — |
| `EmailVerified(memberId)` | 인증 커밋 후 | — | (예: 환영 알림) |
| `PasswordResetMailRequested(email, kind, rawToken?, otherProviders)` | 요청 처리 후 | 메일 발송 | — |
| `PasswordResetCompleted(memberId)` | 재설정 커밋 후 | 모든 세션 삭제 | — |

- 이벤트에 담긴 원문 토큰은 메일 본문에만 쓰고 로그·저장소에 남기지 않는다(FR-014). 이벤트 로깅 시 토큰 필드는 마스킹한다.
- 메일 발송 실패는 위 결과를 바꾸지 않는다(헌법 V).

## 4. 메일 종류 (내용 계약)

| 종류 | 수신 | 내용 |
|---|---|---|
| 인증 메일 | 가입 이메일 | 24시간 유효 1회용 링크 `/auth/verify?token=…` |
| 재설정 메일 (LOCAL 있음) | 그 이메일 | 30분 유효 1회용 링크 `/password/reset?token=…` + 같은 이메일의 소셜 계정이 있으면 "그 계정은 Google(GitHub)로 로그인하세요" |
| 소셜 전용 안내 | 그 이메일 | 링크 없이 "이 이메일은 Google(GitHub)로 가입되어 비밀번호가 없어요" |
| (없는 이메일) | — | 보내지 않음. 화면 결과는 같다 |

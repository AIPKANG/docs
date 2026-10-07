# Contract: 화면·HTTP 경로 (SSR, Thymeleaf)

**표현 방식**: Thymeleaf SSR + 폼 제출(`application/x-www-form-urlencoded`), 세션 쿠키, 모든 상태 변경 요청은 CSRF 토큰 필수(research R-1). REST 표현 계층을 쓰는 팀원은 같은 Service를 호출하고 아래 "결과"를 JSON 응답 코드·이유 코드(42 §4)로 옮긴다.

공통 규칙
- 모든 응답에 CSP·`X-Content-Type-Options: nosniff`·`Referrer-Policy: strict-origin-when-cross-origin` (헌법 IV).
- 화면 문구는 spec의 문구를 그대로 쓴다. 실패 문구는 가입 여부와 무관하게 같다(SC-006).
- 폼 검증 실패는 같은 화면을 400으로 다시 그리고 필드별 안내를 붙인다. 비밀번호 원문은 다시 채우지 않는다.
- `redirect` 값은 상대 경로 규칙(research R-12)을 통과할 때만 쓴다.

---

## 1. 이메일 가입·인증

| 메서드·경로 | 입력 | 결과 | 요구사항 |
|---|---|---|---|
| `GET /signup` | — | 가입 화면(이메일, 블로그 주소, 비밀번호, 비밀번호 확인, 닉네임, 약관 2개, 규칙별 ✓ 안내·"최대 16자") | FR-005, FR-015 |
| `POST /signup` | `email`, `handle`, `password`, `passwordConfirm`, `nickname`, `agreeTerms`, `agreePrivacy` | 성공: 계정 생성(미인증) + 로그인(세션 새로 발급) + 커밋 후 인증 메일 → `303 /signup/verify-sent`. 이미 이메일 가입된 주소: 400 + "이미 가입된 이메일이에요. [로그인] [비밀번호 찾기]". 탈퇴 유예 계정의 이메일: 400 + "탈퇴 신청한 계정이 있어요. 로그인하면 복구할 수 있어요"(13 §3). 규칙 위반·약관 미동의: 400 + 필드 안내 | FR-006~FR-008, FR-012, FR-013, SC-001 |
| `GET /signup/verify-sent` | 로그인 필요 | "인증 메일을 보냈어요" + [인증 메일 다시 보내기] | FR-011 |
| `GET /auth/verify?token=` | `token` | 유효: `email_verified_at` 기록 → "인증이 완료됐어요". 만료·사용됨·없음: "링크가 만료됐어요. [인증 메일 다시 보내기]" (같은 화면, 200) | FR-008, FR-010, SC-007 |
| `POST /auth/verify/resend` | 로그인 필요(미인증 회원) | 한도 안: 새 토큰 발송, 이전 링크 무효 → "인증 메일을 다시 보냈어요". 한도 초과: 발송 없이 "잠시 후 다시 시도해 주세요". 이미 인증됨: "이미 인증된 계정이에요" | FR-009 |

> 가입 직후 로그인 여부: 07 §3은 "인증 전에도 로그인은 된다"이므로 가입 성공 시 바로 로그인시킨다(별도 로그인 단계 없음). 세션 ID는 새로 발급한다.

## 2. 로그인·로그아웃

| 메서드·경로 | 입력 | 결과 | 요구사항 |
|---|---|---|---|
| `GET /login` | `redirect`(선택) | 로그인 화면 + [Google로 계속하기] [GitHub로 계속하기] + [비밀번호 찾기] | FR-001 |
| `POST /login` | `email`, `password`, `redirect`(선택) | 성공: 세션 ID 새로 발급, `last_login_at` 갱신 → `303` 저장된 요청 또는 검증된 `redirect` 또는 `/`. 실패: `/login?error` "이메일 또는 비밀번호가 올바르지 않아요". 잠금: "잠시 후 다시 시도해 주세요(약 15분)". IP 한도 초과: 같은 잠금 문구. 정지: "정지된 계정이에요 (~기한, 사유)"(`ACCOUNT_SUSPENDED`). 탈퇴 유예: `303 /account/restore` | FR-024~FR-030, SC-005, SC-006 |
| `POST /logout` | 로그인 필요, CSRF | 세션 삭제·쿠키 삭제 → `303 /` (홈에서 1회 IndexedDB 정리 재실행). 버튼 스크립트가 제출 전 `draft:{memberId}:*`, `draft-backup:{memberId}:*` 삭제 | FR-031, SC-008 |
| `GET /account/restore` | 복구 전용 세션 | 복구 화면([복구하기] [로그아웃]). 복구 동작(`POST /account/restore`)은 회원 탈퇴 기능(13·44) 소유 | FR-030 |

복구 전용 세션에서 `/account/restore`, `/logout`, 정적 자원 외 모든 요청 → `303 /account/restore` (42 P-12).

## 3. 소셜 로그인

| 메서드·경로 | 입력 | 결과 | 요구사항 |
|---|---|---|---|
| `GET /oauth2/authorization/{google\|github}` | — | 공급자 인증 화면으로 이동(`state` 생성, Spring Security 기본) | FR-020 |
| `GET /login/oauth2/code/{google\|github}` | `code`, `state` | `state` 불일치·오류: `/login?error=social` "소셜 로그인에 실패했어요. 다시 시도해 주세요". 연결된 계정: 2절 `POST /login` 성공·정지·탈퇴 유예와 같은 처리. 처음: 세션에 가입 대기 정보(10분) → `303 /signup/social` | FR-020, FR-021 |
| `GET /signup/social` | 가입 대기 정보 필요 | 가입 마무리 화면: 닉네임(소셜 이름 정리해 미리 채움), 블로그 주소(`go-`/`gi-` 고정 + 본문 수정), 약관 2개, "프로필 사진 사용"(기본 체크), GitHub 인증 이메일 없음 → 이메일 입력칸. 같은 이메일 다른 수단 계정 있음 → "이 이메일로 가입한 계정이 이미 있어요. [기존 계정으로 로그인] [새 계정 만들기]". 대기 정보 없음·10분 경과 → `303 /login` + "다시 소셜 로그인해 주세요" | FR-021~FR-023, FR-033 |
| `POST /signup/social` | `nickname`, `handle`, `agreeTerms`, `agreePrivacy`, `useSocialPicture`, `email`(GitHub 이메일 없을 때만) | 성공: 계정 생성(인증된 이메일이면 `email_verified_at` = 가입 시각) + 로그인(세션 새로 발급) + 대기 정보 삭제 → `303 /` (사진 사용 시 사진 복사 단계 화면을 거침, 003). 입력 이메일이면 미인증 + 인증 메일. 동시 완료로 제약 위반: 이미 생긴 계정으로 로그인. 대기 정보 만료: `303 /login` | FR-002, FR-021, FR-022, SC-001 |
| `POST /signup/social/cancel` | 가입 대기 정보 | [기존 계정으로 로그인]: 대기 정보 삭제 → `303 /login` (계정 생성 없음) | FR-033 |

## 4. 비밀번호 찾기·재설정

| 메서드·경로 | 입력 | 결과 | 요구사항 |
|---|---|---|---|
| `GET /password/forgot` | — | 이메일 입력 화면 | FR-016 |
| `POST /password/forgot` | `email` | 항상 같은 화면·문구 "가입된 이메일이면 안내 메일을 보냈어요" (한도 초과·없는 이메일 포함). 메일 내용은 research R-7·spec FR-017 분기 | FR-016~FR-018, SC-006 |
| `GET /password/reset?token=` | `token` | 유효: 새 비밀번호 입력 화면(토큰은 소비하지 않음, 숨은 필드로 유지). 만료·사용됨: "링크가 만료됐어요. [비밀번호 찾기]" | FR-017 |
| `POST /password/reset` | `token`, `password`, `passwordConfirm` | 새 비밀번호 정책 검사 → 통과 시 토큰을 원자적으로 소비(`GETDEL`) → 해시 저장 + 그 회원의 모든 세션 삭제 → `303 /login` "비밀번호를 바꿨어요. 다시 로그인해 주세요". 정책 위반: 400(토큰은 소비하지 않음). 만료·사용됨: 만료 화면 | FR-012, FR-019, SC-003, SC-004, SC-007 |

> 재설정은 로그인 전 기능이라 정지·탈퇴 유예 회원도 할 수 있다(42 §9, 13 §3).

## 5. 보호된 요청에 대한 공통 결과 (다른 기능이 따름)

| 상황 | SSR 결과 | REST 결과 |
|---|---|---|
| 비회원의 쓰기 요청 | `303 /login?redirect={현재 상대 경로}` | 401 `LOGIN_REQUIRED` |
| 인증 전 회원의 쓰기(글·댓글·사진·좋아요·신고) | 403 화면 "이메일 인증 후 이용할 수 있어요" + [인증 메일 다시 보내기] | 403 `EMAIL_NOT_VERIFIED` |
| 탈퇴 유예 회원 | `303 /account/restore` | 403 `ACCOUNT_WITHDRAWN` |

판정 순서는 42 §3, 구현 진입점은 [account-service.md](./account-service.md)의 `AccountGuard`.

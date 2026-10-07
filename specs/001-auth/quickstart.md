# Quickstart: 로그인·로그아웃 (001-auth) 검증 가이드

**Phase 1 산출물** · 작성일 2026-10-07

이 문서는 기능이 끝까지 동작함을 확인하는 **실행·검증 순서**다. 구현 코드·테스트 코드는 담지 않는다. 경로와 결과는 [contracts/web-routes.md](./contracts/web-routes.md), 데이터는 [data-model.md](./data-model.md), Redis 키는 [contracts/redis-keys.md](./contracts/redis-keys.md)를 따른다.

> 저장소에는 아직 애플리케이션 코드가 없다. 아래 명령은 `/speckit-tasks`·`/speckit-implement` 이후의 구성(Gradle, Docker Compose)을 전제로 한다.

---

## 1. 준비

| 항목 | 내용 |
|---|---|
| 런타임 | Java 21, Docker (Compose) |
| 컨테이너 | PostgreSQL 18(V1 기준선을 Flyway로 적용), Redis(AOF `everysec`, `noeviction`), Mailpit(SMTP 1025, 웹 8025) |
| 환경 변수 | `GOOGLE_CLIENT_ID`/`SECRET`, `GITHUB_CLIENT_ID`/`SECRET`, DB·Redis·SMTP 접속값(운영은 Gmail: `MAIL_USERNAME`, `MAIL_PASSWORD`=앱 비밀번호). 비밀값은 환경 변수로만(헌법 IV) |
| OAuth 콜백 | Google·GitHub 개발용 앱에 `http://localhost:8080/login/oauth2/code/{google\|github}` 등록 |
| 쿠키 | 로컬 HTTP에서 `Secure` 쿠키가 동작하도록 `localhost` 사용(브라우저가 localhost는 보안 컨텍스트로 취급) |

```bash
docker compose up -d postgres redis mailpit
./gradlew bootRun            # 애플리케이션 실행
./gradlew test               # 통합 테스트 (Testcontainers: PostgreSQL·Redis·Mailpit)
```

## 2. 시나리오별 확인 (spec 수용 시나리오 대응)

### S1. 이메일 가입 → 인증 전 차단 → 인증 (US1)
1. `/signup`에서 새 이메일(대문자·앞뒤 공백 섞기)로 가입, 약관 2개 동의.
   - 기대: DB `auth_identity`에 `provider='LOCAL'`, `provider_user_id = email = 소문자`, `email_verified_at IS NULL`. `member_agreement`에 `TERMS`·`PRIVACY` 2행. Mailpit에 인증 메일 1통.
2. 로그인된 상태로 글쓰기 요청 → 403 `EMAIL_NOT_VERIFIED` 안내 + [인증 메일 다시 보내기]. DB에 글 행이 생기지 않음(SC-002).
3. 재발송 두 번 연속 → 두 번째는 발송 없음. 첫 메일의 링크 클릭 → "링크가 만료됐어요".
4. 최신 링크 클릭 → "인증이 완료됐어요", `email_verified_at` 기록. 같은 링크 재클릭 → 만료 안내(SC-007).
5. 글쓰기 요청 → 허용.
6. 같은 이메일(대소문자만 다름)로 다시 가입 → "이미 가입된 이메일이에요. [로그인] [비밀번호 찾기]", 계정 수 변화 없음.

### S2. 비밀번호 규칙 (FR-012, FR-013, SC-003)
가입·재설정 각각에서 `Ab1!` (짧음), 17자, 영문 없음, 숫자 없음, 특수문자 없음, 공백 포함, 한글 포함, 이메일 앞부분 포함, `Password1!` → 모두 거부. 안내에 "최대 16자"와 규칙별 ✓ 글자 표시.

### S3. 로그인·잠금·로그아웃 (US2)
1. 없는 이메일과 틀린 비밀번호로 각각 로그인 → 응답 문구가 글자 하나까지 같음(SC-006).
2. 같은 계정 5회 연속 실패 → 올바른 비밀번호도 "잠시 후 다시 시도해 주세요(약 15분)". Redis `auth:login-lock:*` TTL 약 900초. TTL 경과(테스트는 `Clock`·TTL 조정) 후 로그인 성공(SC-005).
3. `/login?redirect=https://evil.example` 및 `//evil.example` → 로그인 후 `/`로 이동. `/login?redirect=/manage/posts` → 그 경로로 이동.
4. 로그인 전후 세션 쿠키 값이 다름(세션 고정 방지). 쿠키 속성 `HttpOnly; Secure; SameSite=Lax`.
5. 글쓰기 화면에서 몇 글자 입력(IndexedDB `draft:{memberId}:*` 생성 확인) → 로그아웃 → 홈 이동, 개발자 도구에서 `draft:{memberId}:*`·`draft-backup:{memberId}:*` 0개(SC-008). 로그아웃한 브라우저의 쓰기 요청 → 로그인 화면(REST는 401).
6. CSRF 토큰 없이 `POST /logout` → 거부.

### S4. 소셜 가입·로그인 (US3)
1. 처음 Google 로그인 → 계정 없이 가입 마무리 화면(닉네임 미리 채움, `go-` 주소, 약관). 10분 이상 두었다가 완료 → 다시 소셜 로그인 안내.
2. 완료 → `auth_identity.provider='GOOGLE'`, `provider_user_id = sub`, `email_verified_at = member.created_at`.
3. 같은 Google 계정으로 다시 로그인 → 새 계정 없음.
4. S1의 이메일과 같은 이메일의 Google 계정으로 처음 로그인 → 마무리 화면에 "이 이메일로 가입한 계정이 이미 있어요. [기존 계정으로 로그인] [새 계정 만들기]"(FR-033). [기존 계정으로 로그인] → 로그인 화면, 계정 수 변화 없음. 다시 들어와 [새 계정 만들기]로 완료 → 별도 계정 생성, 기존 계정과 연결 없음.
5. 인증된 대표 이메일이 없는 GitHub 계정 → 마무리 화면에 이메일 입력칸, 완료 후 미인증 상태 + 인증 메일.
6. 같은 마무리 요청을 동시에 여러 번 제출 → 계정 1개(SC-001, 통합 테스트로 20회 동시).

### S5. 비밀번호 재설정 (US4)
1. 가입된 이메일·없는 이메일로 각각 요청 → 화면 문구 동일. Mailpit: 가입된 쪽만 메일.
2. LOCAL + 같은 이메일 Google 계정이 있는 경우 → 재설정 링크 + "그 계정은 Google로 로그인하세요". Google만 있는 이메일 → 링크 없는 안내만.
3. 브라우저 A·B에서 같은 계정 로그인 → A에서 재설정 링크로 비밀번호 변경 → B의 다음 요청이 로그인 필요(SC-004). Redis에서 그 memberId의 세션 0개.
4. 같은 링크 재사용·31분 뒤 사용 → 변경 불가.

### S6. 계정 상태 (FR-030)
1. `member_suspension`에 진행 중인 정지 행을 넣고 올바른 비밀번호로 로그인 → "정지된 계정이에요 (~기한, 사유)". 틀린 비밀번호면 일반 실패 문구.
2. `ends_at`이 지난 정지만 있는 회원 로그인 → 성공, 그 행의 `lifted_at` 기록(51 기준이면 `status`도 `ACTIVE`).
3. `status='WITHDRAWN'`(유예 중) 회원 로그인 → 복구 화면만, 다른 주소 요청도 복구 화면으로.
4. 정지 회원도 비밀번호 재설정 요청·완료 가능.

## 3. 자동화 테스트 대응

| 범위 | 위치(예정) | 확인 |
|---|---|---|
| 통합(권한·상태) | `src/test/java/com/team/blog/account/**IT` | S1~S6 중 DB·Redis·세션 결과 (Testcontainers, 헌법 VI) |
| 단위 | `PasswordPolicyTest`, `RedirectTargetValidatorTest` | S2, S3-3 |
| 수동 | 실제 Google·GitHub 앱 | S4 (공급자 화면 연동) |

모든 시나리오가 기대대로면 C-AUTH-1 완료 기준 8개(07 §9)를 만족한다.

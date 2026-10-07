# Quickstart: 블로그 주소(아이디)와 닉네임 (002) 검증 가이드

**Phase 1 산출물** · 작성일 2026-10-07

이 문서는 기능이 끝까지 동작함을 확인하는 **실행·검증 순서**다. 구현 코드·테스트 코드는 담지 않는다. 경로와 결과는 [contracts/web-routes.md](./contracts/web-routes.md), Service 계약은 [contracts/account-identity-service.md](./contracts/account-identity-service.md), 데이터·오류 코드는 [data-model.md](./data-model.md)를 따른다.

> 저장소에는 아직 애플리케이션 코드가 없다. 아래 명령은 `/speckit-tasks`·`/speckit-implement` 이후의 구성(Gradle, Docker Compose)을 전제로 하며, 가입 화면은 001-auth와 함께 동작한다.

---

## 1. 준비

| 항목 | 내용 |
|---|---|
| 런타임·컨테이너 | 001 [quickstart](../001-auth/quickstart.md) §1과 같음 (Java 21, PostgreSQL 18 + V1 기준선, Redis, Mailpit) |
| 금칙어 목록 | 개발·테스트는 테스트용 목록(예: `시발`, `씨발`, `병신`, `shit`, `fack`)과 예외 목록(`시발점`, `시발역`)을 `blog.text.banned-words.*` 위치로 지정. 운영 목록은 팀 검토본(research U-3) |
| 시간 조작 | 30일 제한 확인은 통합 테스트의 고정 `Clock` 또는 DB의 `nickname_changed_at`을 과거로 바꿔 확인 |

```bash
docker compose up -d postgres redis mailpit
./gradlew bootRun
./gradlew test --tests '*Handle*' --tests '*Nickname*' --tests '*BannedWord*'
```

## 2. 시나리오별 확인 (spec 수용 시나리오 대응)

### S1. 주소 미리 채우기 (US1-1~5, SC-001, SC-002)
1. 빈 DB에서 08 §3 예시표 순서대로 가입한다(이메일 가입은 `/signup`, Google·GitHub은 소셜 마무리 화면). 난수는 테스트에서 `483920`으로 고정.
   - 기대: 미리 채워진 값이 순서대로 `kim755030`, `kim755030_2`, `go-kim755030`, `gi-kim755030`, `kim755030_3`, `gokim`, `kim_min_seo`, `kim_min`, `admin_2`, `user_483920`, `go-user_483920`, `gi-12345678` (12개 모두 일치).
2. `/signup`에서 이메일을 입력해 주소가 채워지는 것을 본 뒤, 주소 칸을 한 글자 고치고 이메일을 다시 바꾼다 → 주소 칸이 바뀌지 않는다.
3. 주소 칸에 `KIM-Min` 입력 → 화면에 `kimmin`처럼 소문자로, `-`는 들어가지 않는다. 한글 자판 상태에서도 영문으로 입력된다(수동).
4. 소셜 마무리 화면 → `go-`/`gi-`가 고정 글자로 보이고 지울 수 없다.

### S2. 주소 서버 검사·불변 (US1-6·7, FR-003, FR-010, FR-011, SC-003, SC-004)
1. 이메일 가입 `POST /signup`에 `handle=go-kim` → 400 `HANDLE_PREFIX_MISMATCH`. Google 마무리에 본문 `gi-kim` → 같은 오류.
2. `handle=admin`, `handle=kim-min`, `handle=ab`, 금칙어 포함 본문 → 각각 `HANDLE_RESERVED`(+`admin_2` 제안), `HANDLE_INVALID_FORMAT`, `HANDLE_INVALID_FORMAT`, `HANDLE_BANNED_WORD`(제안 없음, 문구에 단어 없음).
3. 같은 `handle`로 동시 가입 20건(통합 테스트) → `member` 1행, 나머지는 409 "방금 다른 분이 이 주소를 사용했어요. `…_2`는 어떠세요?".
4. 가입 후 프로필 저장 요청에 `handle` 필드를 넣어 보낸다 → `member.handle` 그대로. 코드 검색으로 `handle`을 UPDATE하는 경로가 없음을 확인(`Member`에 변경 메서드 없음).
5. 화면 확인에서 "사용할 수 있어요"를 본 뒤 다른 브라우저에서 같은 주소로 먼저 가입 → 원래 브라우저에서 가입 버튼 → 거부 + 대안 제안.

### S3. 닉네임 규칙 (US2, SC-005, SC-006)
1. `/api/nicknames/availability`와 가입 제출 각각에 다음을 넣는다.

   | 입력 | 기대 코드 |
   |---|---|
   | `ㅋㅋ`, `김 민서`, `kim!`, `😀kim`, `김`, `가나다라마바사아자차카` | `NICKNAME_INVALID_FORMAT` |
   | `12345` | `NICKNAME_LETTER_REQUIRED` |
   | `관리자김`, `admin123`, `Official`, `운영팀장`, `adm1n`(1→i 변형) | `NICKNAME_RESERVED` |
   | `시1발`, `sh1t`, `병1신왕`, (`1`을 `l` 자리에 쓴 영문 금칙어) | `NICKNAME_BANNED_WORD` |
   | `시발점` | 사용 가능 |
2. macOS에서 복사한 NFD `김민서` 제출 → 저장값이 NFC `김민서`(DB에서 `length(nickname) = 3`).
3. `Kim` 회원이 있을 때 `kim`, `KIM` → `NICKNAME_DUPLICATE`, `KIM2` → 허용.
4. 모든 거부 응답 본문·화면에 금칙어 문자열이 없다(통합 테스트에서 응답 본문 검사).
5. 같은 닉네임(대소문자만 다름 포함) 동시 가입 20건 → 성공 1건, 나머지 "방금 다른 분이 이 닉네임을 사용했어요".

### S4. 소셜 이름 미리 채우기 (US2-8)
Google·GitHub 이름(테스트에서는 고정 프로필)을 `Kim Min-seo` / `김민서 (Minseo)` / `Christopher Columbus` / `A` / `Kim`(이미 있음)으로 → 마무리 화면 닉네임 `KimMinseo` / `김민서Minseo` / `Christophe` / 빈칸 + "닉네임을 입력해 주세요" / 빈칸. GitHub `name`이 비면 `login` 기준으로 채워진다.

### S5. 닉네임 변경 30일 (US3, SC-007)
1. 가입 직후 회원이 닉네임 변경 → 성공, `nickname_changed_at` 기록.
2. 바로 다시 다른 닉네임 → 409 `NICKNAME_CHANGE_TOO_SOON`, 설정 화면 입력칸 비활성화 + "다음 변경 가능일".
3. 같은 회원이 지금 닉네임 그대로 저장 → 변화 없음, `nickname_changed_at` 그대로(제한 중이어도 다른 칸 저장은 성공 — 003).
4. 1번에서 버린 이전 닉네임으로 다른 회원이 바로 가입 → 허용.
5. `nickname_changed_at`을 30일 전으로 → 변경 성공. 그 직후 `kim`→`Kim`(대소문자만) → 변경으로 처리되고 다시 30일 제한 시작, 자기 자신 때문에 중복 오류가 나지 않음.
6. 같은 회원의 변경 요청 2건 동시(제한 없는 상태) → 1건만 `CHANGED`, 다른 1건은 409.
7. 다른 회원의 닉네임을 바꾸는 경로가 없음: 요청에 회원 ID를 넣어도 무시되고 로그인한 본인만 바뀐다. 비회원 → 401.

### S6. 주소 접속·표시·탈퇴 (US4, FR-012, FR-013, FR-026, FR-027)
1. `/@Kim755030` → 301 `Location: /@kim755030`. `/@Kim755030/posts/1?a=b` → `/@kim755030/posts/1?a=b`.
2. `/@nobody123`, `/@없는주소` → 404 공통 화면.
3. 회원을 탈퇴 유예(`status='WITHDRAWN'`, `withdrawn_at` 설정) → `/@그주소` 404, 다른 사람이 그 닉네임·주소로 가입 → 각각 `NICKNAME_DUPLICATE`, `HANDLE_DUPLICATE`.
4. 익명 처리(`nickname = NULL`, `deleted_at` 설정, `handle` 유지) → 그 닉네임으로 가입 허용, 그 주소로 가입은 여전히 `HANDLE_DUPLICATE`(대안 제안).
5. 글 상세 작성자 영역·댓글·블로그 상단에 `김민서 @kim755030`, 글 카드에는 `김민서`. 탈퇴 회원은 "탈퇴한 사용자"(링크 없음). 닉네임 `<b>x</b>`처럼 규칙 밖 값은 저장될 수 없고, 출력은 이스케이프.

### S7. 요청 제한·응답 시간 (FR-009, FR-021, SC-008)
1. 같은 IP에서 `/api/handles/availability`를 1분에 31번 → 31번째 429 + `Retry-After`. 닉네임 API도 별도 버킷으로 같음. Redis `account:handle-check:ip:*` TTL ≤ 60초.
2. 가입 화면에서 입력을 멈춘 뒤 결과 표시까지 1초 이내(브라우저 개발자 도구 네트워크 시간).

## 3. 자동화 테스트 대응

| 범위 | 위치(예정) | 확인 |
|---|---|---|
| 단위 | `src/test/java/com/team/blog/account/unit/HandleRulesTest`, `NicknameRulesTest`, `shared/text/BannedWordFilterTest` | S1-1(1~9단계), S3-1·2, S4 |
| 통합 | `src/test/java/com/team/blog/account/integration/**IT` (Testcontainers PostgreSQL·Redis) | S1-1(10단계), S2, S3-3~5, S5, S6, S7-1 |
| 수동 | 브라우저 | S1-2~4(자동 채움 중단, 소문자 표시, 한글 자판), S7-2 |

모든 시나리오가 기대대로면 08 §8 완료 기준 6개와 09 §11 완료 기준 7개를 만족한다.

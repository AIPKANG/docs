# Contract: 블로그 주소·닉네임 공개 Service (001·003·009·014 등이 호출)

헌법 I: 다른 기능·모듈은 `member` 테이블이나 `MemberRepository`를 직접 쓰지 않고 아래 Service로 주소·닉네임 규칙을 쓴다. 시그니처는 설계 수준의 모양이며 구현 코드는 tasks·implement 단계에서 만든다. 001의 [account-service.md](../../001-auth/contracts/account-service.md) 표의 `HandleService`, `NicknamePolicy` 행이 가리키는 상세 계약이 이 문서다(001 research R-17).

패키지: `com.team.blog.account.application`(Service), `com.team.blog.account.domain`(순수 규칙·값), `com.team.blog.shared.text`(금칙어 필터).

---

## 1. HandleService — 블로그 주소

| 연산 | 입력 → 출력 | 규칙 | 트랜잭션 | 호출자 |
|---|---|---|---|---|
| `prefill(String email, Provider provider)` | → `String` (사용 가능한 주소) | 08 §3 1~10단계. 이메일이 없으면(GitHub 인증 이메일 없음) 8단계 `user_`+6자리부터. 결과는 **그 순간** 비어 있는 주소(확정 아님) | 읽기 | 001 소셜 마무리 화면 렌더링, `POST /api/handles/suggestion` |
| `validateForSignup(String rawHandle, Provider provider)` | → `Handle` / 예외 `HandleViolationException(code, suggestion?)` | 정리(trim·소문자) → 접두어 해석(`-` 없음 + 소셜이면 수단 접두어 부착) → `HANDLE_PREFIX_MISMATCH` → `HANDLE_INVALID_FORMAT` → `HANDLE_RESERVED`(+제안) → `HANDLE_BANNED_WORD` → `HANDLE_DUPLICATE`(+제안). research R-5 | 읽기 (호출자의 가입 트랜잭션 안에서 호출 가능) | 001 `EmailSignupService.signUp`, `SocialSignupService.complete` |
| `checkAvailability(String rawHandle)` | → `HandleCheckResult(available, reason?, suggestion?)` | 접두어 일치 검사만 빼고 `validateForSignup`과 같은 순서. `reason`은 `INVALID_FORMAT`/`RESERVED`/`BANNED_WORD`/`DUPLICATE`(코드에서 `HANDLE_` 접두 제외) | 읽기 | `GET /api/handles/availability` |
| `suggestAlternative(Handle base)` | → `String` | 후보 `base_2` … 를 20개씩 `IN` 조회해 비어 있는 첫 값. 본문이 길면 끝을 잘라 36자 유지(research R-3) | 읽기 (새 트랜잭션) | `validateForSignup`, `MemberUniqueViolationTranslator` |
| `canonicalPath(String pathHandle)` | → `Optional<String>` | 대문자가 있으면 소문자 값, 없으면 empty. 존재 여부는 보지 않는다 | 없음 | `shared.web.HandlePathCanonicalizer` |

불변식
- `HandleService`에는 **기존 회원의 주소를 바꾸는 연산이 없다.** `Member.handle`은 생성자에서만 설정한다(FR-011).
- 금칙어 검사 결과 예외·응답에 걸린 단어를 담지 않는다.

## 2. NicknamePolicy — 닉네임 검사 (한곳)

| 연산 | 입력 → 출력 | 규칙 | 호출자 |
|---|---|---|---|
| `validate(String raw, Long excludeMemberId /* nullable */)` | → `Nickname` / 예외 `NicknameViolationException(code)` | ① trim + NFC ② `NICKNAME_INVALID_FORMAT` ③ `NICKNAME_LETTER_REQUIRED` ④ `NICKNAME_RESERVED` ⑤ `NICKNAME_BANNED_WORD` ⑥ `NICKNAME_DUPLICATE`(lower 비교, `excludeMemberId` 제외). 첫 실패에서 멈춘다 | 001 가입 2종(`excludeMemberId = null`), `NicknameChangeService`(본인 id) |
| `check(String raw, Long excludeMemberId)` | → `NicknameCheckResult(normalized, violation?)` | `validate`와 같은 순서, 예외 대신 결과 값 | `GET /api/nicknames/availability` |
| `suggestFromSocialName(String displayName)` | → `Optional<String>` | NFC → 허용 외 제거 → 10자 → `check` 통과 시만 값(research R-13) | 001 소셜 마무리 화면 렌더링 |

- ①~⑤는 `account.domain.NicknameRules`(순수 함수)가, ⑥만 저장소 조회를 한다.
- 003의 소개(`bio`) 금칙어 검사는 `NicknamePolicy`가 아니라 아래 `BannedWordFilter`를 직접 쓴다(예약어 검사 제외, 11 R-3).

## 3. NicknameChangeService — 닉네임 변경

| 연산 | 입력 → 출력 | 규칙 | 트랜잭션 | 호출자 |
|---|---|---|---|---|
| `change(long memberId, String raw)` | → `NicknameChangeResult` (`CHANGED(changedAt)` / `UNCHANGED`) / 예외 `NicknameChangeTooSoonException(nextAllowedAt)`, `NicknameViolationException` | 행 잠금(`FOR UPDATE`) → 같은 값이면 `UNCHANGED` → 30일 검사 → `NicknamePolicy.validate(raw, memberId)` → 저장(`nickname_changed_at = now`). research R-12 | `REQUIRED` (호출자 트랜잭션에 참여, 예외 시 호출자 전체 롤백) | 003 `ProfileService` (`PATCH /api/me/profile` 또는 SSR 설정 화면) |
| `nextAllowedAt(long memberId)` | → `Optional<Instant>` | `nickname_changed_at + 30일`이 미래면 그 값 | 읽기 | 003 설정 화면(입력칸 비활성화·"다음 변경 가능일") |

- `memberId`는 호출자가 `CurrentUser`에서 꺼낸 값만 넘긴다(헌법 III). 요청 본문의 회원 식별자를 받는 연산은 없다.
- 호출 전 권한 검사: `AccountGuard.requireLoggedIn(currentUser)` (인증 전 회원도 변경 가능, 42 §9).

## 4. BlogOwnerResolver · MemberSummaryQuery — 주소로 찾기·표시용 조회

| 연산 | 입력 → 출력 | 규칙 | 호출자 |
|---|---|---|---|
| `BlogOwnerResolver.resolve(String handle)` | → `Optional<BlogOwner>` | 소문자 handle 정확 일치 + `status <> 'WITHDRAWN'`. 형식에 맞지 않는 값은 조회 없이 empty. empty면 호출자가 `NotFoundException`(404) | 009 블로그 페이지, 010 글 상세(`/@{handle}/posts/{id}`의 handle 확인) |
| `MemberSummaryQuery.findByIds(Collection<Long> ids)` | → `Map<Long, AuthorDisplay>` | 한 번의 `IN` 조회(N+1 금지). 탈퇴·익명 처리 회원도 포함(표시만 "탈퇴한 사용자") | 회원 정보를 따로 조인하지 않는 기능(알림 등) |

## 5. AuthorDisplay — `닉네임 @블로그주소` 표시

| 항목 | 내용 |
|---|---|
| 생성 | `AuthorDisplay.of(handle, nickname, withdrawnAt)` — 목록·댓글 읽기 쿼리가 가져온 `m.handle, m.nickname, m.withdrawn_at`으로 만든다 |
| `fullLabel()` | 정상: `{nickname} @{handle}` / 탈퇴: `탈퇴한 사용자` |
| `shortLabel()` | 정상: `{nickname}` / 탈퇴: `탈퇴한 사용자` |
| `blogPath()` | 정상: `/@{handle}` / 탈퇴: 없음(링크 없음) |
| Thymeleaf 조각 | `templates/fragments/author.html` — `byline(author)`(글 상세 작성자 영역·댓글·블로그 상단), `name(author)`(글 카드). `th:text`만 사용 |

## 6. MemberUniqueViolationTranslator — 경합에서 진 쪽 안내

| 연산 | 입력 → 출력 | 규칙 |
|---|---|---|
| `translate(DataIntegrityViolationException e, SignupContext ctx)` | → `HandleTakenException(suggestion)` / `NicknameViolationException(NICKNAME_DUPLICATE, concurrent=true)` / `ExistingSocialAccount(memberId)` / 원래 예외 재던짐 | 제약 이름으로 분기: `uq_member_handle` → 대안 계산(새 읽기 트랜잭션), `uq_member_nickname` → 경합 문구. `ctx`가 소셜 가입이면 먼저 `(provider, provider_user_id)` 계정 존재 확인 → 있으면 `ExistingSocialAccount`(001 R-8: 그 계정으로 로그인). `uq_auth_identity`는 001 규칙 그대로 |
| `translate(DataIntegrityViolationException e)` | 닉네임 변경용 | `uq_member_nickname` → `NicknameViolationException(NICKNAME_DUPLICATE, concurrent=true)` |

- **호출 위치는 트랜잭션 경계 바깥**이다(PostgreSQL은 제약 위반 후 같은 트랜잭션에서 조회할 수 없다). 001 가입 Service는 트랜잭션 메서드를 감싸는 바깥 메서드에서, 003은 `ProfileService` 바깥(표현 계층 또는 파사드)에서 호출한다.
- 가입·변경 Service는 `saveAndFlush`로 위반을 메서드 안에서 드러낸다.

## 7. shared.text.BannedWordFilter · TextVariants

| 연산 | 입력 → 출력 | 규칙 |
|---|---|---|
| `BannedWordFilter.containsBanned(String text)` | → `boolean` | 소문자 → 4변형(그대로 / 숫자 제거 / 0→o·1→i·3→e·4→a·5→s·7→t / 1→l) → 각 변형에서 예외 단어 제거 → 부분 문자열이 금칙어 집합에 있으면 true. **걸린 단어를 반환하지 않는다** |
| `BannedWordFilter.containsBannedInHandleBody(String body)` | → `boolean` | 본문 그대로 + `_`를 지운 값 둘 다 `containsBanned` |
| `TextVariants.of(String lowerText)` | → `List<String>` (4개) | 예약어 포함 검사와 금칙어 검사가 공유 |

- 목록 파일이 없거나 비면 애플리케이션 시작 실패.
- 003(소개), 향후 Tier C(댓글 필터)도 이 컴포넌트를 쓴다.

## 8. 예외 → 응답 매핑 (`shared.error.GlobalExceptionHandler`에 추가)

| 예외 | SSR (가입·설정 폼) | REST |
|---|---|---|
| `HandleViolationException(code, suggestion)` | 같은 화면 400 + 주소 칸 안내(+ "`{suggestion}`는 어떠세요?" 누르면 채워짐) | 400 `{ code, suggestion }` |
| `HandleTakenException(suggestion)` | 같은 화면 409 + "방금 다른 분이 이 주소를 사용했어요. `{suggestion}`는 어떠세요?" | 409 `HANDLE_TAKEN_CONCURRENTLY` |
| `NicknameViolationException(code, concurrent)` | 400(경합이면 409) + 닉네임 칸 안내 | 400/409 `{ code }` (003은 `VALIDATION_FAILED.errors[]` 안에 `field: "nickname"`으로 담음, 11 §5) |
| `NicknameChangeTooSoonException(nextAllowedAt)` | 409 + "다음 변경 가능일: M월 d일" | 409 `NICKNAME_CHANGE_TOO_SOON` + `nextAllowedAt` |
| `RateLimitedException` (사용 가능 여부 API) | — | 429 `RATE_LIMITED` + `Retry-After` |

## 9. 도메인 이벤트

이 기능은 새 도메인 이벤트를 발행하지 않는다. 닉네임 변경은 목록·상세·댓글이 회원 정보를 조인해 보여주므로 즉시 반영되고(11 §5 "반영"), 구독자가 필요 없다.

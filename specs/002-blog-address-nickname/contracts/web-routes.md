# Contract: 화면·HTTP 경로 (블로그 주소·닉네임)

**표현 방식**: 001과 같이 Thymeleaf SSR + 세션 쿠키. 가입 화면의 실시간 확인만 작은 JSON API를 JS(`static/js/account/handle-field.js`, `availability.js`)가 부른다. REST 표현 계층을 쓰는 팀원은 같은 Service([account-identity-service.md](./account-identity-service.md))를 호출하고 아래 결과를 그대로 옮긴다.

공통 규칙 (001 [web-routes.md](../../001-auth/contracts/web-routes.md)와 같음)
- 모든 응답에 CSP·`nosniff`·`Referrer-Policy` (헌법 IV). 닉네임·주소 출력은 이스케이프만.
- 상태를 바꾸는 요청은 CSRF 토큰 필수. 아래 `GET` API는 상태를 바꾸지 않는다.
- 금칙어 거부의 응답 본문·화면·로그에 걸린 단어를 넣지 않는다(SC-006).

---

## 1. 실시간 확인 API (로그인 불필요)

| 메서드·경로 | 입력 | 응답 (200) | 그 밖 | 요구사항 |
|---|---|---|---|---|
| `GET /api/handles/availability?handle=` | `handle`: 전체 주소(접두어 포함) | `{ "available": true, "reason": null, "suggestion": null }` / `{ "available": false, "reason": "DUPLICATE", "suggestion": "kim755030_2" }`. `reason` ∈ `INVALID_FORMAT`·`RESERVED`·`BANNED_WORD`·`DUPLICATE`. `suggestion`은 `RESERVED`·`DUPLICATE`일 때만 | 1분 30회 초과(IP): 429 `{ "code": "RATE_LIMITED" }` + `Retry-After` | FR-009, 08 §4-2 |
| `POST /api/handles/suggestion` | JSON `{ "email": "…" }`, CSRF 헤더 | `{ "handle": "kim_min_seo" }` — 이메일 가입(LOCAL) 기준 미리 채움 값(08 §3 10단계까지 적용된, 그 순간 비어 있는 주소). 이메일 형식이 아니면 `{ "handle": null }` | 주소 확인과 같은 IP 버킷, 429 | FR-006, FR-007 (research R-4) |
| `GET /api/nicknames/availability?nickname=` | `nickname` | `{ "available": true, "code": null }` / `{ "available": false, "code": "NICKNAME_DUPLICATE" }`. `code`는 09 §3 다섯 가지 중 첫 실패. 로그인 상태면 자기 자신은 중복에서 제외 | 1분 30회 초과(IP): 429 | FR-021, 09 §6 |

- 응답은 사용 가능 여부만 알려준다. 블로그 주소·닉네임은 공개 정보라 가입 여부를 따로 드러내지 않는다(08 §4-2). `POST /api/handles/suggestion`도 이메일의 가입 여부가 아니라 공개된 주소의 사용 여부만 반영한다.
- 화면 호출 시점: 입력이 0.5초 멈추면, 이전 요청은 취소(SC-008: 1초 안에 결과 표시).

## 2. 가입 화면 (001 경로에 이 기능이 넣는 칸)

| 화면 (001 경로) | 이 기능의 칸·동작 | 요구사항 |
|---|---|---|
| `GET /signup` | 블로그 주소 칸 `devlog.com/@[ … ]` + 안내 "이메일 앞부분으로 미리 채웠어요. 이메일을 드러내고 싶지 않으면 바꿔 주세요." + "⚠ 블로그 주소는 가입 후 바꿀 수 없어요." 이메일 입력이 0.5초 멈추면 `POST /api/handles/suggestion` 결과로 채움. **사용자가 주소 칸을 직접 고치면 이후 자동 채움 중단.** 입력 중 대문자는 소문자로 바꿔 보여주고, `-`·허용 외 문자는 입력되지 않게 막는다(`inputmode="latin"`, `autocapitalize="off"`, `lang="en"`로 한글 자판에서도 영문 입력 유도). 닉네임 칸은 빈칸(미리 채우지 않음) | FR-006~FR-009, FR-021, FR-022 |
| `POST /signup` | `handle`, `nickname` 서버 재검사(`HandleService.validateForSignup(handle, LOCAL)`, `NicknamePolicy.validate(nickname, null)`). 실패: 같은 화면 400 + 칸별 안내(대안 제안 있으면 버튼). 경합: 409 + "방금 다른 분이 이 주소를 사용했어요. `…_2`는 어떠세요?" / "방금 다른 분이 이 닉네임을 사용했어요" | FR-003, FR-010, FR-018~FR-020 |
| `GET /signup/social` | 주소 칸: `devlog.com/@go-[ 본문 ]` — 접두어는 고칠 수 없는 고정 글자, 본문만 입력. 초기값은 `HandleService.prefill(인증된 이메일, provider)`의 본문. 닉네임 칸: `NicknamePolicy.suggestFromSocialName(소셜 이름)` 결과, 없으면 빈칸 + "닉네임을 입력해 주세요" | FR-007, FR-022 |
| `POST /signup/social` | `handle`은 **본문만**(접두어는 서버가 수단에 맞게 붙임). 본문에 `-`가 섞여 오면 접두어로 해석해 수단과 다르면 `HANDLE_PREFIX_MISMATCH`. 나머지는 `POST /signup`과 같다. 같은 소셜 계정 동시 제출은 001 R-8대로 기존 계정 로그인 | FR-003, FR-010 |

## 3. 블로그 주소 접속

| 메서드·경로 | 결과 | 요구사항 |
|---|---|---|
| `GET /@{handle}` 및 `/@{handle}/**` (handle에 대문자 포함) | **301** → 같은 경로에서 handle만 소문자(쿼리 유지). 예: `/@Kim755030` → `/@kim755030`, `/@Kim755030/posts/12?x=1` → `/@kim755030/posts/12?x=1`. 존재 여부를 보기 전에 실행 | FR-012, SC-004 |
| `GET /@{handle}` (소문자) | `BlogOwnerResolver.resolve` 결과가 있으면 블로그 페이지(내용은 009). 없음·형식 불일치(`/@없는주소`)·탈퇴 유예·익명 처리 → 공통 404 "볼 수 없는 페이지예요" | FR-012, FR-013, 42 §9 |

## 4. 닉네임 변경 (경로는 003 소유)

| 메서드·경로 (003) | 이 기능이 정하는 결과 | 요구사항 |
|---|---|---|
| 설정 화면 `GET` (003) | `NicknameChangeService.nextAllowedAt`이 있으면 닉네임 입력칸 비활성화 + "다음 변경 가능일: 11월 1일"(Asia/Seoul 날짜). 블로그 주소는 읽기 전용 "@kim755030 (변경할 수 없어요)" | FR-011, FR-023 |
| `PATCH /api/me/profile` 또는 SSR 저장 (003) | 닉네임이 들어 있으면 `NicknameChangeService.change(currentUser.memberId, nickname)`. 같은 값: 변화 없음(제한 시작 안 함). 30일 이내 다른 값: **409 `NICKNAME_CHANGE_TOO_SOON`**, 요청 전체 실패(11 §5 R-8). 규칙 위반: 400(003의 `VALIDATION_FAILED.errors[]`). 경합: 409 "방금 다른 분이 이 닉네임을 사용했어요". 비회원: 401(SSR은 로그인 화면) | FR-023~FR-025, SC-007 |

- 어떤 경로에도 `handle`을 바꾸는 입력이 없다. 프로필 저장 요청에 `handle` 필드가 와도 무시한다(바인딩 대상에 없음).

## 5. 표시 (다른 기능 화면에 들어가는 조각)

| 위치 (소유 기능) | 조각 | 결과 |
|---|---|---|
| 글 상세 작성자 영역 (010), 댓글 (014), 블로그 상단 (009) | `fragments/author :: byline(author)` | `김민서 @kim755030` (주소 누르면 `/@kim755030`). 탈퇴: "탈퇴한 사용자"(링크 없음) |
| 글 카드 (009) | `fragments/author :: name(author)` | `김민서`. 탈퇴: "탈퇴한 사용자" |

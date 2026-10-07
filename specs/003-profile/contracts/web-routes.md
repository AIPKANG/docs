# Contract: 화면·HTTP 경로 (프로필·계정 설정·프로필 이미지)

**표현 방식**: 001·002와 같이 Thymeleaf SSR + 세션 쿠키. 설정 화면의 저장·업로드는 JS(`static/js/profile/*.js`)가 아래 JSON API를 부른다. REST 표현 계층을 쓰는 팀원은 같은 Service([profile-service.md](./profile-service.md))를 호출하고 아래 결과를 그대로 옮긴다.

공통 규칙 (001 [web-routes.md](../../001-auth/contracts/web-routes.md))
- 모든 응답에 CSP·`nosniff`·`Referrer-Policy`. CSP는 저장소 출처와 소셜 사진 호스트를 `img-src`·`connect-src`에 더한다(research R-12).
- 상태를 바꾸는 요청은 CSRF 헤더(`X-CSRF-TOKEN`) 필수.
- 경로에 회원 식별자가 없다. 대상은 로그인 정보로만 정한다(FR-002, 헌법 III).
- 권한 오류: 비회원 401 `LOGIN_REQUIRED`(SSR은 `/login?redirect=…`로 303), 탈퇴 유예 403 `ACCOUNT_WITHDRAWN`, 인증 전(사진만) 403 `EMAIL_NOT_VERIFIED`.

---

## 1. 화면

| 메서드·경로 | 결과 | 요구사항 |
|---|---|---|
| `GET /settings` | 로그인: 설정 화면(11 §2). 비회원: 303 `/login?redirect=/settings` | FR-001, FR-003, FR-029 |
| `GET /settings/social-picture` | 로그인 필요. 세션의 거른 소셜 사진 주소를 한 번 꺼내 `data-picture-url`로 넘기고 지운다. 값이 없으면 303 `/` | FR-021, FR-022 |
| `GET /signup/social` (001) | `pictureUrl`은 `SocialPictureUrlPolicy`를 통과한 256 크기 주소만. 통과 못 하면 사진 칸·미리보기 없음 | FR-019, FR-020 |
| `POST /signup/social` (001) | 완료 후: "프로필 사진 사용" + 거른 주소 있음 + 공급자 인증 이메일 → 303 `/settings/social-picture`, 그 밖 303 `/` | FR-021 |
| `GET /@{handle}` (002) | 블로그 상단에 프로필 아이콘(이미지 또는 기본 아이콘)·`닉네임 @주소`·소개(이스케이프, 줄바꿈만 유지, 자동 링크 없음) | FR-008, FR-009, FR-018, SC-002 |

설정 화면 구성: 프로필 아이콘 + [이미지 변경](인증 전이면 숨기고 안내) + [기본 이미지로], 닉네임(30일 제한 중이면 비활성 + "다음 변경 가능일: 11월 1일"), 소개(글자 수 `n / 200`), 블로그 주소 `@주소`(변경할 수 없어요), [저장] / 이메일(변경할 수 없어요), 로그인 수단, [비밀번호 변경](LOCAL만), 새 글 기본 공개 범위 라디오 / [회원 탈퇴] 진입 링크.

## 2. 프로필 API

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `GET /api/me/profile` | — | 200 `ProfileView`: `{ handle, nickname, bio, profileImageId, profileImageUrl, email, provider, emailVerified, defaultVisibility, nicknameNextAllowedAt }` | 401 |
| `PATCH /api/me/profile` | `{ "nickname"?: string\|null, "bio"?: string\|null, "profileImageId"?: number\|null }` — 보낸 칸만. `profileImageId: null` = 기본 이미지로, `bio: null` = 비움, `nickname: null` = 보내지 않음과 같음 | 200 `ProfileView`(저장 후 값) | 400/409 `VALIDATION_FAILED` + `errors[]`(모든 실패 칸), 401, 403 `ACCOUNT_WITHDRAWN` |

```json
{ "code": "VALIDATION_FAILED", "message": "입력한 내용을 확인해 주세요",
  "errors": [
    { "field": "bio", "code": "BIO_TOO_LONG", "message": "소개는 200자까지 쓸 수 있어요" },
    { "field": "nickname", "code": "NICKNAME_CHANGE_TOO_SOON", "message": "다음 변경 가능일: 11월 1일", "nextAllowedAt": "2026-11-01T03:00:00Z" },
    { "field": "profileImageId", "code": "INVALID_PROFILE_IMAGE", "message": "사용할 수 없는 이미지예요" } ] }
```

- 하나라도 실패하면 아무것도 바뀌지 않는다(SC-001). HTTP는 400, 오류가 모두 `NICKNAME_CHANGE_TOO_SOON`·동시 경합 `NICKNAME_DUPLICATE`뿐이면 409.
- 알 수 없는 칸(`handle`, `email` 등)은 무시한다 — 바꾸는 경로가 없다(FR-003).
- 금칙어 거부 응답에 걸린 단어나 입력값을 넣지 않는다.

## 3. 프로필 이미지 업로드 API (media, 008이 확장)

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/images/presign` | `{ "purpose": "PROFILE", "contentType": "image/webp", "size": 18234, "originalName": "me.jpg" }` | 200 `{ "imageId": 87, "uploadUrl": "http://localhost:9000/blog-images/images/2026/10/…webp?X-Amz-…", "method": "PUT", "headers": { "Content-Type": "image/webp" }, "expiresAt": "…" }` (5분) | 401, 403 `EMAIL_NOT_VERIFIED`/`ACCOUNT_WITHDRAWN`, 429 `RATE_LIMITED`(1분 20장), 400 `IMAGE_INVALID`(`detail`: `TYPE`·`SIZE`), 400 `IMAGE_PURPOSE_NOT_SUPPORTED`(`POST`) |
| (브라우저 → 저장소) `PUT {uploadUrl}` | 본문 = 이미지, 헤더 `Content-Type` 일치 | 200 | 서명 위조·만료·형식 불일치 403(저장소) |
| `POST /api/images/{id}/complete` | — | 200 `{ "imageId": 87, "url": "http://localhost:9000/blog-images/images/2026/10/….webp", "width": 256, "height": 256 }` (이미 완료면 같은 응답) | 401, 403, 404(없음·남의 것), 400 `IMAGE_INVALID`(`detail`: `MISSING`·`SIZE`·`CONTENT_MISMATCH`·`DIMENSION`·`METADATA`) — 이때 저장소 파일·행 삭제 |

## 4. 계정 API

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/me/password` | `{ "currentPassword", "newPassword", "newPasswordConfirm" }` | 204. 지금 기기 세션 ID 새로 발급(쿠키 갱신), 다른 기기 세션 삭제, 알림 메일 | 400 `PASSWORD_NOT_SUPPORTED`(소셜), 429 `PASSWORD_CHANGE_LOCKED` + `Retry-After`, 400 `CURRENT_PASSWORD_MISMATCH`, 400 `PASSWORD_SAME_AS_CURRENT`, 400 `VALIDATION_FAILED`(`newPassword`: 001 `password.*` 코드, `newPasswordConfirm`: `PASSWORD_MISMATCH`), 401 |
| `PATCH /api/me/settings` | `{ "defaultVisibility": "PUBLIC" \| "PRIVATE" }` | 200 `{ "defaultVisibility": "PRIVATE" }` | 400 `VALIDATION_FAILED`(`INVALID_VISIBILITY`), 401 |

## 5. 표시 조각

| 조각 | 입력 | 출력 |
|---|---|---|
| `fragments/avatar :: avatar(avatar, size)` | `ProfileAvatar`, 픽셀 크기 | 이미지: `<img class="avatar" src=… alt="" width height>` / 기본: `<span class="avatar avatar-c{0..7}" aria-hidden="true">김</span>` |
| `fragments/author :: avatar(author, size)` | `AuthorDisplay`(002, `profileImageUrl` 추가) | 탈퇴면 기본 회색 아이콘, 아니면 위와 같음 |

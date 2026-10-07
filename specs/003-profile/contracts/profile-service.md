# Contract: 공개 Service (account 프로필·계정, media 이미지)

다른 모듈·표현 계층은 아래 Service만 부른다(헌법 I). `memberId`는 항상 `CurrentUser`에서 꺼낸 값이다(헌법 III).

---

## 1. account.application

### ProfileService

```text
ProfileView view(CurrentUser user)
    // AccountGuard.requireLoggedIn. 설정 화면·GET /api/me/profile

ProfileView update(CurrentUser user, ProfileUpdateCommand command)
    // AccountGuard.requireLoggedIn (닉네임·소개는 인증 전도 허용, 42 §9).
    // 1) 보낸 칸 전부 사전 검사 → 실패 모음이 있으면 ProfileValidationException(errors), 쓰기 없음
    // 2) 한 트랜잭션: member 행 잠금 → NicknameChangeService.change(REQUIRED) → bio →
    //    ProfileImageService.attach/detach → member.profile_image_id·profile_image_url
    // 3) uq_member_nickname 경합은 트랜잭션 밖에서 NICKNAME_DUPLICATE(동시) 오류 항목으로 번역
    // @throws ProfileValidationException, LoginRequiredException, AccountStatusException(ACCOUNT_WITHDRAWN: 세션 필터가 먼저 막음)
```

### BioPolicy

```text
BioCheckResult check(String raw)   // normalized + 첫 위반(BIO_TOO_LONG → BIO_TOO_MANY_LINES → BIO_BANNED_WORD) 또는 없음
```

### PasswordChangeService

```text
void change(CurrentUser user, PasswordChangeCommand command, String currentSessionId)
    // 순서: LOCAL 아님 → PasswordNotSupportedException
    //       잠금 → PasswordChangeLockedException(retryAfter)
    //       현재 비밀번호 불일치 → CurrentPasswordMismatchException (실패 +1, 5회째 15분 잠금)
    //       새 = 현재 → PasswordSameAsCurrentException
    //       정책·확인 → ProfileValidationException(newPassword / newPasswordConfirm)
    // 성공: 해시 교체, 실패 수 삭제, PasswordChanged 이벤트
    // 커밋 후: SessionRevoker.revokeAllExcept(memberId, currentSessionId), 알림 메일(mail/password-changed)
    // 호출자(표현 계층)는 성공 뒤 지금 세션 ID를 새로 발급한다(request.changeSessionId()).
```

### AccountSettingsService

```text
String defaultVisibility(long memberId)                       // 글 기능(004·005)이 새 글 시작 값으로 읽는다
String changeDefaultVisibility(CurrentUser user, String value) // PUBLIC | PRIVATE, 그 밖 → ProfileValidationException(INVALID_VISIBILITY)
```

### SessionRevoker (001, 메서드 추가)

```text
int revokeAll(long memberId)                              // 001 그대로
int revokeAllExcept(long memberId, String keepSessionId)  // 003: 비밀번호 변경. keepSessionId 세션만 남긴다
```

### 표시 값

```text
ProfileAvatar(handle, nickname, imageUrl): initial(), colorIndex() ∈ 0..7, hasImage()
AuthorDisplay.of(handle, nickname, withdrawnAt, profileImageUrl)   // 002 3인자 팩토리 유지
BlogOwner(memberId, handle, nickname, bio, profileImageUrl).avatar()
```

### ImageReferenceLookup 구현 (media SPI)

```text
ProfileImageReferences implements media.application.ImageReferenceLookup
Set<Long> referencedIds(Collection<Long> imageIds)   // member.profile_image_id IN (...) 한 번
```

## 2. media.application

### ImageStorage (인터페이스, 04 §4-1)

```text
UploadTarget prepareUpload(String key, String contentType, long size)
Optional<StoredObject> inspect(String key)
String publicUrl(String key)
void delete(String key)
```
구현: `media.infra.S3ImageStorage`(MinIO·S3 호환, SigV4). 008: `LocalImageStorage` 추가.

### ImageUploadService

```text
PresignResult presign(CurrentUser user, PresignCommand command)
    // AccountGuard.requireWritable → 1분 20장 → purpose(PROFILE만) → 형식·크기 → image 행(TEMP) → 5분 PUT 주소
CompleteResult complete(CurrentUser user, long imageId)
    // 본인 아니면 NotFoundException → 완료면 같은 결과 → inspect(트랜잭션 밖) → 규격 검사
    // 실패: 저장소 파일 삭제 + 행 삭제 + ImageInvalidException(detail)
    // 통과: width·height·size_bytes 기록
```

### ProfileImageService (account가 부름, 호출자 트랜잭션에 참여)

```text
boolean isValidCandidate(long memberId, long imageId)   // 본인·PROFILE·완료·detached_at NULL
String attach(long memberId, long imageId)               // 이미지 행 FOR UPDATE + 재판정 → ATTACHED, detached_at NULL → 공개 주소
                                                         // 실패 시 InvalidProfileImageException
void detach(long imageId)                                // detached_at = now (Clock)
```

### ImageCleanupService

```text
CleanupReport runOnce()
    // 고아 키 재시도 → TEMP 24h·detached 7d 후보 → ImageReferenceLookup으로 참조 중 제외
    // → 행 조건부 삭제(짧은 트랜잭션) → 커밋 후 저장소 삭제(실패 키는 img:orphan-keys)
```
`ImageCleanupJob`: `@Scheduled(cron = blog.image.cleanup.cron, zone = Asia/Seoul)`, `blog.image.cleanup.enabled`.

## 3. 이벤트

| 이벤트 | 필드 | 구독(커밋 후) |
|---|---|---|
| `PasswordChanged` (shared.event) | `memberId`, `email`, `keepSessionId` | account: 다른 세션 삭제, 알림 메일 |

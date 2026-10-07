# Quickstart: 프로필 수정·계정 설정 (003) 검증 가이드

**Phase 1 산출물** · 작성일 2026-10-07

기능이 끝까지 동작함을 확인하는 **실행·검증 순서**다. 경로·결과는 [contracts/web-routes.md](./contracts/web-routes.md), Service는 [contracts/profile-service.md](./contracts/profile-service.md), 데이터·오류 코드는 [data-model.md](./data-model.md).

---

## 1. 준비

| 항목 | 내용 |
|---|---|
| 런타임·컨테이너 | 001·002 quickstart §1 + 저장소 `storage`(`pgsty/silo:RELEASE.2026-09-16T00-00-00Z`) |
| 포트 | 기본 포트가 다른 프로젝트와 겹치면 `DB_PORT`, `REDIS_PORT`, `MAIL_PORT`, `MAILPIT_WEB_PORT`, `STORAGE_PORT`를 바꿔 띄우고 앱에도 같은 값을 준다 |
| 저장소 | 개발 프로필은 시작할 때 버킷 `blog-images`를 만들고 익명 읽기(`images/*`)만 연다(`blog.storage.create-bucket=true`) |
| Docker | Colima면 Gradle 테스트가 소켓을 자동으로 찾는다(002 I-7) |

```bash
DB_PORT=55432 REDIS_PORT=56379 MAIL_PORT=51025 MAILPIT_WEB_PORT=58025 STORAGE_PORT=59000 docker compose up -d
DB_PORT=55432 REDIS_PORT=56379 MAIL_PORT=51025 STORAGE_PORT=59000 ./gradlew bootRun
./gradlew clean build          # 전체 테스트(통합 테스트는 Testcontainers로 저장소까지 띄운다)
```

## 2. 시나리오별 확인

### S1. 닉네임·소개 한 번에 저장 (US1, SC-001, SC-002, SC-007)
1. 로그인 → `/settings` → 닉네임·소개 고쳐 [저장] → 200, `/@주소` 상단에 새 닉네임·소개.
2. 정상 닉네임 + 201자 소개 → 400 `VALIDATION_FAILED`, `bio: BIO_TOO_LONG`, 닉네임 그대로(DB 확인).
3. 금칙어 소개 + 형식 틀린 닉네임 + 남의 이미지 ID → 세 칸 오류가 한 응답에.
4. 소개 `<script>alert(1)</script>` 저장 → `/@주소` HTML에 `&lt;script&gt;`로 나오고 실행되지 않음.
5. 5줄 소개 → `BIO_TOO_MANY_LINES`. `a\n\n\n\nb`(빈 줄 연속) → 3줄로 줄어 저장.
6. 30일 제한 중: 소개만 저장 → 성공. 같은 요청에 다른 닉네임 → 409, 소개도 그대로.
7. 로그아웃 상태 `/settings` → `/login?redirect=/settings`. `PATCH /api/me/profile`에 `handle`·`email`을 넣어도 값 그대로.

### S2. 프로필 이미지 (US2, SC-003, SC-004)
1. 설정 화면에서 사진 선택 → 자르기(끌기·확대) → 미리보기 → [저장] → `member.profile_image_url`이 `http://localhost:9000/blog-images/images/…webp`, 파일 256×256 WebP(브라우저 수동 확인).
2. presign만 하고 저장하지 않음 → 시계를 24시간 넘겨 정리 실행 → 행·파일 삭제.
3. 연결된 이미지 → 정리 여러 번 → 남음. 교체 또는 [기본 이미지로] → 7일 넘겨 정리 → 이전 이미지 삭제.
4. 300×300 PNG, 1MiB 넘는 파일, PNG를 `image/webp`로 신고, EXIF가 든 JPEG → complete 400 `IMAGE_INVALID`, 파일 삭제.
5. 남의 이미지·`purpose=POST` 행·정리된 이미지 ID로 저장 → `INVALID_PROFILE_IMAGE`, 프로필 그대로.
6. 이미지 없는 회원 → 동그란 기본 아이콘, 닉네임 첫 글자(영문 대문자), 주소마다 같은 색.
7. 인증 전 회원 presign → 403 `EMAIL_NOT_VERIFIED`. 소개 저장은 성공. 1분 21번째 presign → 429.

### S3. 소셜 사진 (US3, SC-005)
1. Google 가입 마무리 화면(테스트 도우미로 대기 정보 주입) → 미리보기 주소가 `https://lh3.googleusercontent.com/…=s256-c`. 다른 호스트·`http` 주소 → 사진 칸 없음.
2. 사진 사용 체크 → 가입 완료 → `/settings/social-picture`로 이동 → (브라우저) 사진 복사 → `/`. 저장된 주소가 우리 저장소 주소.
3. 사진 받기 실패(5초 초과·차단) → 가입 유지, 기본 아이콘, "소셜 사진을 가져오지 못했어요. 설정에서 직접 올릴 수 있어요".
4. 체크 해제 → `/`, 기본 아이콘.

### S4. 비밀번호·기본 공개 범위 (US4, SC-006)
1. 같은 계정으로 브라우저 A·B 로그인 → A에서 변경 → A 세션 ID 바뀌고 로그인 유지, B는 다음 요청에서 로그인 필요, Mailpit에 "비밀번호가 변경됐어요" 메일.
2. 현재 비밀번호 5회 틀림 → 6번째 429 `PASSWORD_CHANGE_LOCKED`(맞는 비밀번호여도).
3. 새 = 현재 → `PASSWORD_SAME_AS_CURRENT`. `short1!` → `VALIDATION_FAILED`(`newPassword`).
4. 소셜 계정 → 화면에 버튼 없음, API 400 `PASSWORD_NOT_SUPPORTED`.
5. `PATCH /api/me/settings {"defaultVisibility":"PRIVATE"}` → `AccountSettingsService.defaultVisibility` = `PRIVATE`. `FRIENDS` → 400.

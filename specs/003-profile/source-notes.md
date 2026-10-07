# 003-profile 원문 참고 노트

## plan 단계에서 참고할 기술 결정

- 설정 화면 경로 `/settings` — 11 §2
- 프로필 저장 `PATCH /api/me/profile {nickname, bio, profileImageId}` (`profileImageId: null` = 기본 이미지), 오류 형식 `VALIDATION_FAILED` + `errors[]` — 11 §5
- 비밀번호 변경 `POST /api/me/password {currentPassword, newPassword, newPasswordConfirm}` — 11 §6-2
- 기본 공개 범위 `PATCH /api/me/settings {"defaultVisibility": ...}`, `member.default_visibility` — 11 §6-3, 06 §5
- 스키마: `member.bio varchar(200)` + CHECK, `member.profile_image_id` FK(nullable, `image` 생성 뒤 ALTER TABLE), `member.profile_image_url` 유지(목록 JOIN 회피), `image.purpose` (`POST`/`PROFILE`, 기본 `POST`) — 11 §7, §4-4
- 연결/해제 한 트랜잭션: 새 이미지 `status = ATTACHED`, 이전 이미지 `detached_at = now()`; 정리 배치 TEMP 24h·detached 7일 — 11 §4-4, 04 §4-4
- 업로드 흐름: presign `{purpose: PROFILE}` → 업로드 → complete(매직 바이트, 256×256, 1MB) — 11 §4-1, 04 §4-1
- 브라우저 변환: 256×256 WebP 품질 0.85 — 11 §4-1
- 소셜 사진: 허용 호스트 `lh3.googleusercontent.com`, `avatars.githubusercontent.com`; Google `=s256-c`, GitHub `&s=256`; `crossOrigin="anonymous"`, 5초 제한; 서버는 외부 URL 요청 안 함(SSRF 회피) — 11 §4-2
- 기본 이미지는 SVG/CSS, 8색 팔레트(handle 해시) — 11 §4-3
- 비밀번호 변경 후 세션 ID 재발급, 다른 세션 삭제 — 11 §6-2
- 권한 표 — 42 §9
- 파일 저장소는 MinIO(S3 API, SigV4 Presigned URL) — 01 §4 결정 기록 2026-10-06, 04 §6-1

# Source Notes: 012-access-permission

원문: `docs/42-permission-matrix.md` 전체 (§1 P-1~P-12, §2 행위자, §3 판정 순서, §4 응답 코드, §5~§10-3 표, §11 화면, §12 완료 기준)

## plan 단계에서 참고할 기술 결정

- 권한 검사는 Service 계층에서 매번 (02 §5, 42 P-1). 표현 계층(SSR+세션 / REST+JWT)은 팀원별 (01 Q2)
- 행위자 판별 컬럼: `auth_identity.email_verified_at`, `member.status`(ACTIVE/SUSPENDED/WITHDRAWN), `member.role = ADMIN`, `member.withdrawn_at`, `deleted_at`, `author_id` (42 §2, ERD 변경 없음)
- 읽기 판정 단일화: `PostAccessPolicy.canRead(post, viewer)`, 목록은 `VisibilityFilter.forViewer(viewer, author)` 공용 조건(`deleted_at IS NULL` + 작성자 `withdrawn_at IS NULL`), `VisibilityRule` Bean 추가로 확장 (06 §7 R-1~R-3, R-2a)
- 숨김 글 제외: 공용 조건·`canRead`에 `hidden_at IS NULL`(작성자 제외) 추가 — 43 "기존 결정 변경", 화요일 전원 동의 대기 (43 §4-1)
- ③·④는 `author_id = :me` 조건 조회 한 번으로 함께 처리 가능 (42 §3)
- 404 예외 하나로 통일 `PostNotFoundException`, 없음/권한 없음 구분 로그 메시지를 사용자에게 보내지 않음 (06 R-4)
- 비공개 글 응답 `Cache-Control: private, no-store` (06 R-5)
- 정지 시 Redis 세션 전부 삭제 (42 P-7), 탈퇴 유예는 SSR 복구 화면 리다이렉트 / REST 403 (42 P-12)
- REST 오류 본문 `code` 필드에 이유 코드 (42 §4). 공통 오류 응답 형식은 화요일 안건 2
- 재신고 중복 방지 `UQ(reporter_id, target_type, target_id)` (42 §8, 43)
- 좋아요 요청 제한 1분 60번 → 429 (42 §7, 30 §6)
- 권한 매트릭스 통합 테스트: Testcontainers PostgreSQL + Spring Security Test, 06 §8 매트릭스에 행동 축 추가 (42 §12, 헌법 VI)

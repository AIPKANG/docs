# 002-blog-address-nickname 원문 참고 노트

## plan 단계에서 참고할 기술 결정

- 주소 정규식(DB CHECK와 앱 검증 동일): `^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$`, `ck_member_handle` — 08 §2, §7
- 접두어·가입 수단 일치는 가입 Service에서 검사(`auth_identity`라 CHECK 불가) — 08 §2
- 주소 생성 알고리즘 10단계 및 실행 검증된 예시표 — 08 §3
- 주소 중복 확인 API `GET /api/handles/availability?handle=…` → `{ available, reason, suggestion }` — 08 §4-2
- 동시 가입은 `member.handle` UNIQUE로 보장 — 08 §4-2
- 예약어 목록은 설정 파일 — 08 §5
- 탈퇴 익명 껍데기: `handle` 보존, `nickname = null` — 13 D-8~D-10, §3 7단계
- 닉네임 스키마: `nickname varchar(10)`, `nickname_changed_at`, `ck_member_nickname`, `uq_member_nickname ON member (lower(nickname))` — 09 §10
- 검사는 `NicknamePolicy` 한곳(가입·소셜 가입·프로필 수정 공용) — 09 §3
- 금칙어 파일 `banned-words.txt`, `banned-words-exceptions.txt` (운영자 화면 생기면 DB, Tier C) — 09 §4-1
- 닉네임 중복 확인 API `GET /api/nicknames/availability?nickname=…` → `{ available, code }` — 09 §6
- 변경 제한 오류 `409 NICKNAME_CHANGE_TOO_SOON` — 09 §8, 42 §4
- 대문자 주소 접속 301 리다이렉트 — 08 §6

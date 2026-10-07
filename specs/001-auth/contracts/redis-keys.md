# Contract: Redis 키 (auth)

07 §3·§4-1·§6의 키 구조를 따르고, 토큰은 원문 대신 SHA-256 해시를 키에 쓴다(research R-5). 수치는 모두 설정값(`blog.auth.*`)이며 아래는 기본값이다. 다른 모듈은 이 키를 직접 읽거나 쓰지 않는다.

| 키 | 값 | TTL | 쓰는 곳 | 한도·규칙 |
|---|---|---|---|---|
| `auth:verify:{sha256(token)}` | memberId | 24h | 인증 메일 발송 / `GETDEL`로 사용 | 1회용 (FR-008) |
| `auth:verify-current:{memberId}` | 현재 인증 토큰 해시 | 24h | 재발송 시 이전 토큰 삭제 | 이전 링크 무효 (FR-009) |
| `auth:verify-resend:{memberId}:min` | 횟수 | 1분 | 재발송 | 1회 / 1분 |
| `auth:verify-resend:{memberId}:{yyyyMMdd}` | 횟수 | 1일 | 재발송 | 10회 / 1일 (07 §3 키) |
| `auth:reset:{sha256(token)}` | memberId | 30m | 재설정 메일 / `GETDEL`로 사용 | 1회용 (FR-017) |
| `auth:reset-current:{memberId}` | 현재 재설정 토큰 해시 | 30m | 재요청 시 이전 토큰 삭제 | research R-5 |
| `auth:reset-req:email:{sha256(email)}:min` | 횟수 | 1분 | 비밀번호 찾기 | 1회 / 1분 (FR-018) |
| `auth:reset-req:email:{sha256(email)}:{yyyyMMdd}` | 횟수 | 1일 | 비밀번호 찾기 | 10회 / 1일 |
| `auth:reset-req:ip:{ip}` | 횟수 | 1h | 비밀번호 찾기 | 20회 / 1시간 |
| `auth:login-fail:{sha256(email)}` | 연속 실패 수 | 15m (실패마다 연장) | 로그인 실패 | 성공 시 삭제 |
| `auth:login-lock:{sha256(email)}` | 1 | 15m | 5회째 실패 시 설정 | 잠금 중 로그인 거부 (FR-025). 없는 이메일도 동일 |
| `auth:login-ip:{ip}` | 횟수 | 1m | 로그인 시도 | 20회 / 1분 |
| Spring Session 키 (`spring:session:*`, 인덱스 저장소) | 세션 | 마지막 활동 후 14d | 로그인 세션 | principal 이름 = memberId (FR-024, FR-019) |

규칙
- 카운터 증가와 TTL 설정은 하나의 Lua 스크립트로 원자 처리한다.
- 이메일은 trim + 소문자로 정규화한 뒤 해시한다. 키에 원문 이메일·토큰·비밀번호를 넣지 않는다(FR-014).
- Redis는 AOF `everysec`, `noeviction`(헌법 기술 제약). Redis 장애 시 로그인·가입은 실패로 응답한다(세션 저장소가 없으므로). 이는 핵심 기능 자체의 의존이라 헌법 V(부가 기능)의 대상이 아니다.

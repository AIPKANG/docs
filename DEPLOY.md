# 배포 안내

앱은 Docker 이미지 하나(Spring Boot, 운영 프로필)이고 PostgreSQL 18·Redis·S3 호환 저장소(MinIO)·SMTP(Gmail)를 쓴다.
비밀값은 이미지·저장소에 넣지 않고 `.env`(또는 서버 환경 변수)로만 준다.

## 1. 준비
1. `.env.example`을 `.env`로 복사해 값을 채운다. 키 만드는 법은 `specs/001-auth/secrets-setup.md`.
2. 운영 주소가 정해지면 `APP_BASE_URL=https://도메인`으로 바꾸고, Google·GitHub OAuth 앱에 콜백 `https://도메인/login/oauth2/code/google|github`를 추가한다.
3. 운영 저장소(NHN MinIO)에는 버킷 `blog-images`와 공개 읽기 정책을 미리 만들고, 브라우저 업로드를 위해 CORS에 사이트 주소를 허용한다(`STORAGE_CREATE_BUCKET=false`).
4. Redis는 AOF `everysec`, `maxmemory-policy noeviction`이어야 한다(자동 저장 버퍼).

## 2. 한 서버에서 전부 띄우기(Docker Compose)
```bash
docker compose --profile app up -d --build
```
- 앱 `:8080`, PostgreSQL·Redis·저장소는 같은 compose 안. DB 스키마는 앱이 처음 뜰 때 Flyway가 만든다.
- 관리형 DB·Redis·저장소를 쓰면 `.env`에 `DB_URL`(jdbc 주소)·`REDIS_HOST`·`STORAGE_ENDPOINT`를 넣고 `app` 서비스만 띄운다.
- 앱은 저장소를 내부 주소(`STORAGE_ENDPOINT`)로 부르고, 브라우저 업로드 서명은 `STORAGE_PRESIGN_ENDPOINT`, 사진 주소는 `STORAGE_PUBLIC_BASE_URL`(둘 다 바깥에서 닿는 주소). 같으면 PRESIGN은 비워도 된다.
- 로그인 쿠키는 운영 프로필에서 `Secure`라 실제 서비스는 HTTPS(앞단 Nginx·로드밸런서)로 연다.

## 3. 확인
- 앱 로그에 `Started BlogApplication`, 필수 값이 비면 `RequiredSecretsCheck`가 기동을 멈춘다.
- 가입 → 인증 메일 수신 → 글쓰기·사진 올리기·발행 → 글 상세에서 사진이 보이는지.
- 관리자 지정: `UPDATE member SET role = 'ADMIN' WHERE handle = '주소';`

## 4. 예약 작업
여러 대로 늘려도 Redis 잠금으로 한 대만 실행한다: 자동 저장 반영(1분), 조회수 반영(1분), 트렌딩(10분), 빈 임시글·휴지통·사진·알림·신고·탈퇴 정리(매일 새벽, 한국 시간).

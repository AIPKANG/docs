# 배포 안내

앱은 Docker 이미지 하나(Spring Boot, 운영 프로필)이고 PostgreSQL 18·Redis·SMTP(Gmail)를 쓴다.
**사진은 처음에는 앱 서버 디스크(Docker 볼륨 `app-data`)에 저장**하고, 서버를 여러 대로 늘릴 때 S3 호환 저장소(MinIO)로 확장한다.
비밀값은 이미지·저장소에 넣지 않고 `.env`(또는 서버 환경 변수)로만 준다.

## 1. 준비
1. `.env.example`을 `.env`로 복사해 값을 채운다. 키 만드는 법은 `specs/001-auth/secrets-setup.md`.
   - `STORAGE_LOCAL_SECRET`: 사진 업로드 주소 서명 키. `openssl rand -hex 32`로 만든 값. 바꾸면 진행 중인 업로드만 실패한다.
2. 운영 주소가 정해지면 `APP_BASE_URL=https://도메인`, `SITE_DOMAIN=도메인`으로 바꾸고, Google·GitHub OAuth 앱에 콜백 `https://도메인/login/oauth2/code/google|github`를 추가한다.
3. Redis는 AOF `everysec`, `maxmemory-policy noeviction`이어야 한다(자동 저장 버퍼). compose의 Redis는 이미 그렇게 뜬다.

## 2. 띄우기
- 로컬에서 전체 시험: `docker compose --profile app up -d --build` (앱 `:8080`)
- 운영 서버: `deploy/README.md` 순서(서버 준비 → `deploy/first-deploy.sh`로 HTTPS 인증서와 전체 기동). DB·Redis는 바깥에 열지 않고 Nginx만 80·443.
- 로그인 쿠키는 운영 프로필에서 `Secure`라 실제 서비스는 HTTPS로 연다.

## 3. 확인
- 앱 로그에 `Started BlogApplication`. 필수 값이 비면 `RequiredSecretsCheck`가 기동을 멈춘다(디스크 저장이면 `STORAGE_LOCAL_SECRET`, MinIO면 접속값 4개).
- 가입 → 인증 메일 → 글쓰기·사진 올리기·발행 → 글 상세에서 사진(`/media/images/...`)이 보이는지.
- 관리자 지정: `UPDATE member SET role = 'ADMIN' WHERE handle = '주소';`
- 백업: DB(`pg_dump`)와 함께 `app-data` 볼륨(사진)도 백업한다.

## 4. 예약 작업
여러 대로 늘려도 Redis 잠금으로 한 대만 실행한다: 자동 저장 반영(1분), 조회수 반영(1분), 트렌딩(10분), 빈 임시글·휴지통·사진·알림·신고·탈퇴 정리(매일 새벽, 한국 시간).

## 5. 확장
- **서버 키우기**: 사양만 올린다.
- **DB·Redis 떼어 내기**: 관리형 서비스로 옮기고 `.env`에 `DB_URL`(jdbc 주소)·`DB_USERNAME`·`DB_PASSWORD`·`REDIS_HOST`를 넣는다. 코드 변경 없음.
- **앱 서버 여러 대**: 세션·예약 작업은 이미 Redis로 공유된다. 단, **사진이 디스크에 있는 동안은 앱 서버를 한 대로 유지**하고, 늘리기 전에 아래 "사진 저장소 확장"을 먼저 한다.

### 사진 저장소 확장(디스크 → MinIO/S3)
코드는 바꾸지 않는다. 사진 저장은 `ImageStorage` 하나 뒤에 디스크(`local`)와 S3 호환(`s3`) 두 구현이 있고 설정으로 고른다.
1. MinIO(NHN 제공 또는 compose의 `storage`)에 버킷 `blog-images`, 익명 읽기 정책, 사이트 주소 CORS(PUT)를 만든다.
2. 기존 파일 복사: 볼륨의 `/app/data/images/`를 버킷의 `images/`로 그대로 올린다(예: `mc mirror /app/data/images minio/blog-images/images`). `.type` 파일은 빼고, 원래 Content-Type을 지정해 올린다.
3. `.env`: `STORAGE_TYPE=s3`, `STORAGE_ENDPOINT`(앱이 부르는 주소), `STORAGE_PUBLIC_BASE_URL`(브라우저가 보는 주소), 필요하면 `STORAGE_PRESIGN_ENDPOINT`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`.
4. DB의 사진 주소를 새 주소로 바꾼다(`/media/` → `{공개주소}/blog-images/`): `post.content_md`·`post_draft.content_md`·`post.thumbnail_url`·`member.profile_image_url`. 그다음 `post.render_version`을 0으로 두면 다시 렌더링 작업이 `content_html`을 새로 만든다.
5. 앱을 다시 띄우고 글 상세·프로필 사진을 확인한 뒤, 볼륨의 옛 파일을 지운다.

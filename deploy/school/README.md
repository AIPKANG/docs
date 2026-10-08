# 학교 공용 서버 배포 (서버 배포 설명서 10-08)

`main`에 올리면 GitHub Actions가 **테스트 → 도커 이미지 생성 → SSH로 서버에 보내 `run-on-server.sh` 실행**까지 한다.
서버에는 우리 앱 컨테이너와 Redis 컨테이너만 뜬다. DB는 Crowfoot PostgreSQL, HTTPS·도메인은 서버의 공용 Nginx가 맡는다.

## 0. 워크플로 파일 넣기

배포 워크플로는 `deploy/school/github-deploy.yml`에 있다. 문서 레포(AIPKANG/docs)에서는 돌지 않게 `.github` 밖에 두었다.
**소스코드 레포**에 `.github/workflows/deploy.yml`로 복사해서 올린다. 개인 접근 토큰(PAT)으로 push하면 토큰에 `workflow` 권한이 있어야 한다(없으면 "without `workflow` scope"로 거절). 권한을 늘리기 어렵다면 GitHub 웹에서 Add file → Create new file로 같은 경로에 붙여 넣어도 된다.

## 1. GitHub Repository secrets (Settings → Secrets and variables → Actions)

| 이름 | 넣을 값 |
|---|---|
| `SSH_ADDRESS` | aip 공통 인프라 문서의 서버 주소(학교 와이파이: 내부망, 핫스팟: 외부) |
| `SSH_PORT` | `8822` |
| `SSH_ID` | 문서에서 자기 계정 |
| `SSH_PASSWORD` | 문서 최상단 공용 비밀번호 |
| `APP_PORT` | 받은 포트 번호(Nginx `proxy_pass`와 같게) |
| `APP_DOMAIN` | 받은 도메인(예: `blogcabin.java21.net`, https 없이) |
| `DB_ADDRESS` | Crowfoot 데이터베이스 탭 맨 위 주소에서 `:숫자`를 뺀 부분 |
| `DB_PORT` | 그 주소 뒤의 숫자 |
| `DB_NAME` | 데이터베이스 |
| `DB_USERNAME` | 계정 |
| `DB_PASSWORD` | 비번 |
| `DB_SCHEMA` | 스키마 탭의 스키마(이 프로젝트: `cf_u39_d1`) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | `.env`의 Gmail 주소·앱 비밀번호 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | `.env` 값 |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | `.env` 값 |
| `GEMINI_API_KEY` | (선택) AI 태그 추천 |

사진 주소 서명용 비밀값(`STORAGE_LOCAL_SECRET`)은 서버가 처음 배포할 때 스스로 만들어 `~/team-blog/storage-secret`에 보관한다.

## 2. 서버의 Nginx 설정 파일

서버에 SSH로 들어가 `~/nginx/<도메인>.conf`를 만든다(팀 문서의 설정 내용을 복사). 두 곳만 바꾼다.
- `server_name` → 받은 도메인
- `proxy_pass`의 포트 → `APP_PORT`

앱은 `X-Forwarded-Proto`·`X-Forwarded-For`를 믿으므로(https 판단·접속 IP), 설정에 아래 줄이 없으면 `location /` 안에 넣는다.

```
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
client_max_body_size 12m;
```

## 3. 로그인 콜백 주소

Google·GitHub 개발자 콘솔에 운영 주소를 추가한다.
- Google: `https://<도메인>/login/oauth2/code/google`
- GitHub: `https://<도메인>/login/oauth2/code/github`

## 4. 배포·확인

- 자동: `main`에 push. 수동: GitHub Actions → deploy → Run workflow(급하면 테스트 건너뛰기).
- 서버에서 상태: `docker ps`, 로그: `docker logs --tail 100 team-blog-app`
- 처음 켜질 때 Crowfoot 스키마에 테이블 20개가 만들어진다(Flyway). 검색에 쓰는 `pg_trgm` 확장이 DB에 없고 만들 권한도 없으면 이때 실패하니 로그를 본다.
- 실패하면 GitHub inbox(오른쪽 위 프로필 왼쪽 아이콘)의 오류를 복사해 Claude에게 보낸다.

로컬에서 같은 스크립트를 가짜 Crowfoot DB(스키마만 있는 PostgreSQL)에 돌려 테이블 생성·홈·검색이 되는 것을 확인했다(10-08).

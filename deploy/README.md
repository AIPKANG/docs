# 운영 서버 배포 (서버 한 대 + Docker Compose + Nginx·Let's Encrypt)

1. 서버(Ubuntu 24.04, 2vCPU·4GB 이상, 22·80·443 열기)에 접속해 `deploy/setup-server.sh` 내용을 실행한다(Docker 설치, 저장소 내려받기). 한 번 나갔다 다시 접속.
2. 도메인 두 개의 A 레코드를 서버 IP로: 블로그(예: `blog.example.com`)와 사진(예: `img.example.com`).
3. `~/blog/.env`를 `.env.example`대로 채운다. 운영 값:
   - `APP_BASE_URL=https://블로그도메인`, `SITE_DOMAIN=블로그도메인`, `STORAGE_DOMAIN=사진도메인`, `STORAGE_PUBLIC_URL=https://사진도메인`, `CERT_EMAIL=알림받을메일`
   - `DB_PASSWORD`, `STORAGE_ROOT_USER`·`STORAGE_ROOT_PASSWORD`는 새로 만든 긴 무작위 값
   - 메일·OAuth·Gemini 키
4. `~/blog/deploy/first-deploy.sh` — http로 Nginx → 인증서 발급 → 전체 HTTPS 기동.
5. Google·GitHub OAuth 앱에 콜백 `https://블로그도메인/login/oauth2/code/google|github` 추가.
6. 새 버전: `~/blog/deploy/update.sh`. 인증서 갱신: crontab에 `0 4 * * 1 ~/blog/deploy/renew-cert.sh`.

DB·Redis·저장소 포트는 바깥에 열지 않는다(Nginx만 80·443). 자체 AI(Ollama)는 4GB 서버에서는 띄우지 않는다(AI 추천은 Gemini만, 한도가 다 되면 "지금은 추천할 수 없어요").

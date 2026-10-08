#!/usr/bin/env bash
# 서버에서 첫 배포: ① http만 여는 Nginx ② Let's Encrypt 인증서 발급 ③ HTTPS 설정으로 전체 기동.
# 필요: ~/blog/.env 에 SITE_DOMAIN, STORAGE_DOMAIN, CERT_EMAIL, APP_BASE_URL=https://SITE_DOMAIN,
#       STORAGE_PUBLIC_URL=https://STORAGE_DOMAIN 와 비밀값들(.env.example 참고).
set -euo pipefail
cd ~/blog
set -a; . ./.env; set +a
C="docker compose -f compose.yaml -f compose.prod.yaml"
# ① 인증서 없이 http만(앱 없이 Nginx만)
NGINX_TEMPLATES=./deploy/nginx/bootstrap $C --profile app up -d --no-deps nginx
# ② 인증서(블로그·사진 주소 둘 다)
$C --profile certbot run --rm certbot certonly --webroot -w /var/www/certbot \
  -d "$SITE_DOMAIN" -d "$STORAGE_DOMAIN" --email "$CERT_EMAIL" --agree-tos --no-eff-email
# ③ 전체(HTTPS 설정으로 Nginx를 다시 만든다)
$C --profile app up -d --build --force-recreate nginx app
$C ps

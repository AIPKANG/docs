#!/usr/bin/env bash
# 인증서 갱신(서버 crontab에 매주 등록: 0 4 * * 1 ~/blog/deploy/renew-cert.sh)
set -euo pipefail
cd ~/blog
C="docker compose -f compose.yaml -f compose.prod.yaml"
$C --profile certbot run --rm certbot renew --webroot -w /var/www/certbot
$C exec nginx nginx -s reload

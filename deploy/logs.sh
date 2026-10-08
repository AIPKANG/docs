#!/usr/bin/env bash
# 로그 보기: deploy/logs.sh [시간, 기본 24h] — 앱의 경고·오류만 모아 보고, 컨테이너 상태도 함께 보여 준다.
# 전체 로그를 계속 보려면: docker compose -f compose.yaml -f compose.prod.yaml logs -f app
set -euo pipefail
cd "${BLOG_DIR:-$HOME/blog}"
C="docker compose ${COMPOSE_FILES:--f compose.yaml -f compose.prod.yaml}"
SINCE="${1:-24h}"
$C ps
echo "---- 최근 $SINCE 경고·오류(앱) ----"
$C logs --since "$SINCE" --no-color app | grep -E " (WARN|ERROR) " || echo "없음"
echo "---- 상태 확인 ----"
curl -fsS http://localhost/actuator/health -H "Host: ${SITE_DOMAIN:-localhost}" 2>/dev/null || $C exec -T app curl -fsS http://localhost:8080/actuator/health
echo

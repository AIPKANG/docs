#!/usr/bin/env bash
# 서버에서 새 버전 배포: 최신 main을 받아 앱 이미지만 다시 빌드해 띄운다(DB·사진은 그대로).
set -euo pipefail
cd ~/blog
git pull --ff-only origin main
docker compose -f compose.yaml -f compose.prod.yaml --profile app up -d --build app nginx

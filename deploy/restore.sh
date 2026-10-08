#!/usr/bin/env bash
# 복구: deploy/restore.sh <db-…​.dump> [images-….tar.gz]
# DB를 백업 시점으로 되돌린다(지금 데이터는 지워진다). 앱을 멈추고 복구한 뒤 다시 띄운다.
set -euo pipefail
DB_DUMP="${1:?복구할 db-*.dump 파일을 지정해 주세요}"
IMAGES="${2:-}"
cd "${BLOG_DIR:-$HOME/blog}"
C="docker compose ${COMPOSE_FILES:--f compose.yaml -f compose.prod.yaml}"

read -r -p "지금 DB를 $DB_DUMP 시점으로 덮어써요. 계속할까요? (yes 입력) " answer
[ "$answer" = "yes" ] || { echo "취소했어요"; exit 1; }

$C stop app
$C exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" --if-exists blog && createdb -U "$POSTGRES_USER" blog'
$C exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d blog --no-owner' < "$DB_DUMP"
if [ -n "$IMAGES" ]; then
  # 사진은 앱 볼륨에 풀어 넣는다(같은 이름 파일은 덮어쓴다)
  $C run --rm --no-deps -T --entrypoint sh app -c 'tar -C /app/data -xzf -' < "$IMAGES"
fi
$C start app
echo "복구 완료. 앱이 다시 뜨면 /actuator/health 를 확인하세요."

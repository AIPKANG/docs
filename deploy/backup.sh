#!/usr/bin/env bash
# 백업: DB(pg_dump, 압축)와 사진 볼륨(app-data)을 날짜별로 ~/backups 에 남기고, KEEP_DAYS(기본 14)일 지난 것은 지운다.
# 서버 crontab 예: 30 3 * * * ~/blog/deploy/backup.sh >> ~/backups/backup.log 2>&1
# 다른 곳(외부 저장소)으로도 복사해 두는 것을 권한다: 서버 디스크가 망가지면 같이 사라진다.
set -euo pipefail
cd "${BLOG_DIR:-$HOME/blog}"
C="docker compose ${COMPOSE_FILES:--f compose.yaml -f compose.prod.yaml}"
DEST="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$DEST"

# DB: 사용자 이름은 컨테이너 안 환경 변수에서 읽는다(비밀번호를 명령줄에 쓰지 않는다)
$C exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d blog --format=custom --no-owner' > "$DEST/db-$STAMP.dump"

# 사진: 앱 컨테이너의 /app/data 를 tar로
$C exec -T app tar -C /app/data -czf - . > "$DEST/images-$STAMP.tar.gz"

# 비어 있으면 실패로 본다
[ -s "$DEST/db-$STAMP.dump" ] || { echo "DB 백업이 비었어요"; exit 1; }
[ -s "$DEST/images-$STAMP.tar.gz" ] || { echo "사진 백업이 비었어요"; exit 1; }

find "$DEST" -maxdepth 1 -type f \( -name 'db-*.dump' -o -name 'images-*.tar.gz' \) -mtime +"$KEEP_DAYS" -delete
echo "백업 완료: $DEST/db-$STAMP.dump, $DEST/images-$STAMP.tar.gz"

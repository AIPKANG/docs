# Data Model: 트렌딩 (019)
새 마이그레이션 없음. 읽는 값: `post.like_count`, `post.view_count`, `post.first_public_at`, `comment`(작성자·삭제·숨김). Redis `trending:{스냅샷ID}`(글 ID 목록, 30분), `trending:current`(30분). 오류 410 `SNAPSHOT_EXPIRED`, 400 `INVALID_CURSOR`. 설정 `blog.trending.*`.

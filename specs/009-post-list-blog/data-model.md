# Data Model: 전체 글 목록·개인 블로그 (009)
새 마이그레이션 없음. 읽는 칸: `post.id, title, excerpt, thumbnail_url, first_public_at, comment_count, like_count, author_id`, `member.handle, nickname, profile_image_url, withdrawn_at`. 인덱스 `ix_post_feed`, `ix_post_blog`.
값: `PostCard`, `CardPage(items, nextCursor)`, `FeedCursor(firstPublicAt, id)`. 오류 400 `INVALID_CURSOR`.
설정: `blog.post.list.page-size: 9`(서버 고정), `blog.post.list.restore-minutes: 30`(화면).

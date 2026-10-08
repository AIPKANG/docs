# Data Model: 글 상세 (010)
새 마이그레이션 없음. 읽는 칸: `post.(id, author_id, status, visibility, title, content_html, excerpt, thumbnail_url, published_at, first_public_at, edited_at, view_count, like_count, comment_count, deleted_at)`, `member.(handle, nickname, profile_image_url, bio, withdrawn_at)`, `post_tag`·`tag`, (작성자) `post_draft`·버퍼.
값: `PostDetail`(글·작성자·태그·작성자 관점 정보). 캐시 지시: 공개 `private, no-cache`, 그 밖 `private, no-store`.

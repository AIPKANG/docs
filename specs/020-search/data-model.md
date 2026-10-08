# Data Model: 검색 (020)
새 마이그레이션 없음. V1 `pg_trgm` GIN 인덱스(`ix_post_title_trgm`, `ix_post_content_trgm`, `ix_member_nickname_trgm`, `ix_member_handle_trgm`)와 `ix_post_feed` 사용. Redis `rate:search:visitor:{방문자}`. 검색어는 저장하지 않는다. 설정 `blog.search.*`(recent-window 3000, page-size 9, people-limit 20, per-minute 30).

# Data Model: 태그 (013)
새 마이그레이션 없음. `tag(name UNIQUE, ck_tag_name)`, `post_tag(post_id, tag_id, position)`, 인덱스 `ix_tag_name_prefix`(varchar_pattern_ops), `ix_post_tag_tag`. Redis `tags:top`(10분).

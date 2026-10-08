-- 025 친구 공개 (강성찬 개인 확장, docs/06 §6). 공통 테이블은 CHECK 교체만(03 E-10), friendship은 V1에 이미 있다.

ALTER TABLE post DROP CONSTRAINT ck_post_visibility;
ALTER TABLE post ADD CONSTRAINT ck_post_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'));

ALTER TABLE member DROP CONSTRAINT ck_member_default_visibility;
ALTER TABLE member ADD CONSTRAINT ck_member_default_visibility CHECK (default_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'));

-- 친구 요청 알림(팔로우처럼 묶음)
ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN ('COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'NEW_POST',
    'REPORT_RESOLVED', 'CONTENT_HIDDEN', 'FRIEND_REQUEST'));
ALTER TABLE notification DROP CONSTRAINT ck_notification_group;
ALTER TABLE notification ADD CONSTRAINT ck_notification_group CHECK ((type IN ('LIKE', 'FOLLOW', 'FRIEND_REQUEST')) = (group_key IS NOT NULL));

-- 친구가 보는 개인 블로그 목록(공개 + 친구 공개, 최초 공개 시각이 없으면 발행 시각)
CREATE INDEX ix_post_blog_friends ON post (author_id, (COALESCE(first_public_at, published_at)) DESC, id DESC)
    WHERE status = 'PUBLISHED' AND visibility IN ('PUBLIC', 'FRIENDS') AND deleted_at IS NULL AND hidden_at IS NULL;

COMMENT ON INDEX ix_post_blog_friends IS '친구가 보는 블로그 목록(025)';

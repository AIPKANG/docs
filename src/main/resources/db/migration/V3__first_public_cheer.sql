-- 026 공개 전환(강성찬 개인 확장): 친구 공개였던 글이 처음 전체 공개되면 친구에게 응원 알림. 알림 종류 CHECK 교체만.
ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN ('COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'NEW_POST',
    'REPORT_RESOLVED', 'CONTENT_HIDDEN', 'FRIEND_REQUEST', 'FIRST_PUBLIC'));

# Data Model: 내 글 관리·휴지통 (011)
새 마이그레이션 없음. `post.deleted_at`(휴지통), `ix_post_manage(author_id, status, updated_at DESC) WHERE deleted_at IS NULL`, `ix_post_trash(author_id, deleted_at DESC) WHERE deleted_at IS NOT NULL`. 완전 삭제는 `DELETE FROM post` + FK CASCADE(`post_draft`, `post_like`, `post_view_daily`, `post_tag`, `post_image`, `comment`, `notification`), `report_case.post_id`는 SET NULL.
값: `ManageRow`, `ManagePage(items, nextCursor, counts)`, `ManageTab(DRAFTS, PUBLISHED, TRASH)`.
설정 `blog.post.trash.{retention: 30d, purge-enabled: true, purge-cron: "0 20 5 * * *", purge-batch-size: 100}`, `blog.post.manage.page-size: 20`.

# 004-draft-autosave 원문 참고 노트

## plan 단계에서 참고할 기술 결정

- 3단계: IndexedDB(`localforage` 등) → Redis → PostgreSQL. localStorage 불가(5MB·동기) — 04 §2, §2-2
- 설정 초기값(`application.yml`): 로컬 1초, 서버 3초/최대 30초, DB 반영 1분, Redis TTL 24h, 5초 1회·본문 1MB — 04 §2-1
- IndexedDB 키 `draft:{memberId}:{postId}` `{title, contentMd, baseVersion, dirty, pendingImages, updatedAt}`, 백업 `draft-backup:{memberId}:{postId}` 7일 — 04 §2-2, §2-7
- 서버 전송 트리거 `visibilitychange`, `pagehide` + fetch keepalive, 이탈 경고 `beforeunload` — 04 §2, §2-2
- API `PUT /api/posts/{postId}/autosave {title, contentMd, baseVersion}` → 200 `{version}` / 409 `{server:{...}}` / 404 — 04 §2-3
- Redis Hash `autosave:post:{postId}`(memberId, title, contentMd, version, savedAt) + Set `autosave:dirty`; 버전 확인·저장은 Lua 스크립트로 원자 처리 — 04 §2-3
- 스케줄러 SQL: 임시글은 `post`에 `WHERE status='DRAFT' AND edit_version < :version`, 발행 글은 `post_draft` UPSERT `WHERE post_draft.edit_version < EXCLUDED.edit_version`; Redis 키는 지우지 않음 — 04 §2-4
- 다중 서버 스케줄러 잠금: ShedLock 등 — 04 §2-4
- Redis AOF `appendfsync everysec`, `maxmemory-policy noeviction`, 장애 시 Circuit Breaker로 DB 직접 저장 — 04 §2-6
- 발행 후 Redis 키 삭제는 `@TransactionalEventListener(AFTER_COMMIT)` — 04 §2-5 (상세 05 §7)
- 비교 창 diff: 브라우저 `jsdiff` — 04 §2-7
- 스키마: `post.edit_version`, `post_draft`(post 1:0..1, `edit_version`) — 04 §5, 03-erd.md
- 빈 임시글 배치: 매일 새벽 — 04 §2-5
- 시퀀스 다이어그램 — 04 §3
- 권한 표 — 42 §5-2

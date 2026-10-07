# 005-post-publish 원문 참고 노트

## plan 단계에서 참고할 기술 결정

- API `POST /api/posts/{postId}/publish` + `Idempotency-Key` 헤더(UUID), 본문 `{title, contentMd, tags, visibility, baseVersion}`; 응답 200 `{url, publishedAt, firstPublicAt, editedAt, version}` / 400 / 404 / 409 `VERSION_CONFLICT`·`IN_PROGRESS` / 422 `IDEMPOTENCY_KEY_REUSED` — 05 §5
- 오류 형식 `VALIDATION_FAILED` + `errors[]` — 05 §4
- Idempotency: Redis `SET idem:publish:{memberId}:{key} {hash, IN_PROGRESS} NX EX 600`, 성공 시 응답 저장·실패 시 키 삭제 — 05 §6
- 처리 순서 ①~⑪: 트랜잭션 밖 검증·렌더링(render_version) → `SELECT … FOR UPDATE`(author_id·deleted_at 조건) → 버전 = max(Redis, post_draft, post) → 태그 `INSERT … ON CONFLICT DO NOTHING`·post_tag 교체 → post_image 동기화 → UPDATE(`published_at = COALESCE`, `first_public_at` CASE, `edited_at` CASE) → post_draft 삭제 → AFTER_COMMIT: 조건부 Redis 삭제 Lua, `PostPublished`/`PostEdited` 이벤트, idem 응답 저장 — 05 §7
- JPA 지침 J-1~J-5: 도메인 메서드 `post.publish(...)`, 카운터는 `@Modifying` 증감, `edit_version`에 `@Version` 금지, `@Lock(PESSIMISTIC_WRITE)`, 커밋 후 처리 — 05 §9
- 시각 컬럼 의미(`created_at`, `updated_at`, `published_at`, `first_public_at`, `edited_at`) — 05 §3
- 본문 100,000자 DB CHECK, 태그 설정 `blog.post.max-tags` — 05 §1 P-5·P-6
- 제목 정리 상세(U+200B~U+200F, U+2060~U+2069, U+FEFF, U+202A~U+202E) — 12 §7-4
- 태그 허용 문자·NFKC·금칙어·`TAG_TOO_LONG`·`TAG_BANNED_WORD` 구체화 — 22 (05 §4를 구체화)
- 요약·썸네일 규칙 — 10 §2-1, §6
- 완료 기준 확인 방법(통합 테스트 시나리오) — 05 §10
- 권한 표·판정 순서 — 42 §3, §5-2

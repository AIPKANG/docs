# Contract: 발행 API와 글 상세 (005)

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/posts/{postId}/publish` + 헤더 `Idempotency-Key` | `{ title, contentMd, tags: string[], visibility: "PUBLIC"\|"PRIVATE", baseVersion }` | 200 `{ url: "/@handle/posts/42", publishedAt, firstPublicAt, editedAt, version }` | 400 `VALIDATION_FAILED`+`errors[]`·`CONTENT_TOO_COMPLEX`·`INVALID_REQUEST`, 401, 403, 404, 409 `EDIT_CONFLICT`+`server`·`IN_PROGRESS`, 422 `IDEMPOTENCY_KEY_REUSED` |
| `GET /@{handle}/posts/{postId}` | — | 글 상세(R-8). 발행+공개는 누구나 | 볼 수 없으면 404(없음·비공개·임시·휴지통 구별 없음) |
| `GET /api/posts/{postId}/editing` (004) | — | + `tags`(발행된 태그, 순서대로), `visibility` | 004와 같음 |

`errors[]` 항목: `{ field: "title"|"contentMd"|"tags"|"tags[i]"|"visibility", code, message }`.

## Service (`post.application.PostPublishService`)
`PublishResult publish(Optional<CurrentUser>, long postId, String idempotencyKey, PublishCommand)` — REST·SSR 공용.
`post.application.PostAccessPolicy.canRead(Optional<CurrentUser>, PostView)` — 상세·목록(009) 공용 판정.
`tag.application.PostTagService.replace(long postId, List<String> normalizedNames)`, `tagsOf(long postId)`.

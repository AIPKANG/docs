# Contract: 공개 범위 (006)

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `PATCH /api/posts/{postId}/visibility` | `{ "visibility": "PUBLIC"\|"PRIVATE" }` | 200 `{ visibility, firstPublicAt }` | 400 `INVALID_VISIBILITY`, 401, 403, 404 |
| `GET /@{handle}/posts/{id}` (005) | — | 볼 수 있으면 상세 | 볼 수 없거나 없으면 같은 404(공통 화면, OG "볼 수 없는 글이에요", `noindex`, `no-store`) |
| `GET /manage/posts` (004) | — | + 공개 범위 배지·바꾸기 버튼 | |

공개 구성 요소: `PostAccessPolicy.canRead(viewer, PostFacts)`, `PostAccessPolicy.publicListingCondition(postAlias, memberAlias)`, `VisibilityRule`(Bean 추가로 확장), `PostVisibilityService.change`.

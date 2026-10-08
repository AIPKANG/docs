# Contract: 검색 (020)
| 경로 | 결과 |
|---|---|
| `GET /search?q=&tab=posts\|people&sort=relevance\|latest&cursor=` | 화면(누구나, noindex). `#태그`면 303 `/tags/{이름}` |
| `GET /@{handle}?q=&sort=&cursor=` | 그 블로그 안 검색 화면 |
| `GET /api/search/posts?q=&sort=&cursor=&blog=` | 200 `{items:[{id,url,title,excerpt,snippetHtml,snippetParts,thumbnailUrl,firstPublicAt,commentCount,likeCount,author}], nextCursor?, notice?}` · 400 `INVALID_CURSOR` · 404(없는 블로그) · 429 |
| `GET /api/search/people?q=` | 200 `[{handle,nickname,profileImageUrl,bio}]` · 429 |

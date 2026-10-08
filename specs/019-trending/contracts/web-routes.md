# Contract: 트렌딩 (019)
| 경로 | 결과 |
|---|---|
| `GET /api/posts/trending?cursor={스냅샷}:{위치}` | 200 카드 목록(010 형식) · 400 `INVALID_CURSOR` · 410 `SNAPSHOT_EXPIRED` |
| `GET /?tab=trending[&cursor=…][&expired=1]` | 트렌딩 탭(누구나). 사라진 커서면 안내와 함께 처음부터 |

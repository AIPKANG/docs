# Contract: 팔로우·팔로잉 피드 (018)
| 경로 | 결과 |
|---|---|
| `PUT`·`DELETE /api/members/{handle}/follow` | 200 `{following, followerCount}` · 400 `CANNOT_FOLLOW_SELF` · 401 · 404 · 429 |
| `GET /api/members/{handle}/followers` · `/following` `?cursor=` | 200 `{items:[{handle, nickname, profileImageUrl, bio, followedByMe, me}], nextCursor?}` · 404 |
| `GET /api/feed?cursor=` | 200 카드 목록(010 형식) · 401 |
| `GET /@{handle}/followers` · `/following` | 목록 화면(누구나) · 404 |
| `POST /@{handle}/follow` (`following`, `back`) | 303 원래 화면 |
| `GET /feed` | 피드 화면(비회원 303 로그인) |
| `GET /me/followers` | 303 `/@내주소/followers` |

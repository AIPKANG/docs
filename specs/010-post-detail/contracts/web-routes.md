# Contract: 글 상세 (010)
| 요청 | 결과 |
|---|---|
| `GET /@{HANDLE}/posts/{id}` | 301 소문자(002) |
| `GET /@{handle}/posts/{abc}` | 404 |
| 볼 수 없음·없음·휴지통·탈퇴 유예 작성자 | 404(공통 화면·OG·noindex) |
| `GET /@{다른주소}/posts/{id}` (볼 수 있음) | 301 `/@{작성자}/posts/{id}` |
| 작성자 본인의 임시글 | 302 `/write/{id}` |
| 볼 수 있는 발행 글 | 200 상세(R-3), 공개면 OG·canonical·`private, no-cache`, 아니면 noindex·`private, no-store` |

# Contract: 미리보기 API와 렌더러 (007)

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/markdown/preview` | `{ "contentMd": string }` | 200 `{ "html": string }` — 발행과 같은 렌더러 결과 | 401, 400 `CONTENT_TOO_LONG`/`CONTENT_TOO_COMPLEX`/`INVALID_REQUEST`, 429 `RATE_LIMITED`(+`Retry-After`) |

편집 화면(004 `/write/{id}`)에 [미리보기] 버튼: 누르면 본문 칸 아래에 미리보기 영역을 열고, 입력이 0.5초 멈출 때마다 갱신한다.

## 공개 구성 요소 (005·008·009가 씀)

| 이름 | 계약 |
|---|---|
| `ContentRenderer.render(String contentMd)` | `RenderedContent`. 복잡하면 `ContentTooComplexException` |
| `PostTitleRules.clean(String)` | NFC·숨은 문자·방향 문자·제어 문자 제거·앞뒤 공백 제거 |
| `RerenderService.runBatch()` | 이전 버전 발행 글 최대 100개 다시 렌더링, 처리 수 반환 |
| `MarkdownTransformer`(AST 변환 목록) | 008이 GIF 규칙을 **추가** |

출력 규칙은 `docs/12-content-sanitize.md` §3~§6 그대로(허용 태그·속성 표).

# Data Model: 본문 렌더링·정화 (007)

**새 마이그레이션 없음.** V1 `post`에 `content_md`, `content_html`, `excerpt`(200자), `render_version integer NOT NULL DEFAULT 1`이 이미 있다.

| 값 | 내용 |
|---|---|
| `RenderedContent` | `html`(정화된 본문 HTML), `excerpt`(글자 ≤200자, 없으면 빈 값), `imageUrls`(본문에 나온 우리 저장소 이미지 주소 순서대로, 008·005가 씀), `renderVersion` |
| `ContentRenderer.RENDER_VERSION` | 현재 렌더링 규칙 버전(1). 렌더러·정화 규칙을 바꾸면 올린다 |

오류 코드: `CONTENT_TOO_COMPLEX`(400, 중첩 20 초과·1초 초과), `CONTENT_TOO_LONG`(400), `RATE_LIMITED`(429, 미리보기 1분 60회).

설정(`blog.markdown.*`): `max-nesting: 20`, `render-timeout: 1s`, `site-origin: ${blog.auth.mail.link-base-url}`, `preview-per-minute: 60`, `rerender.enabled: true`, `rerender.cron: "0 10 5 * * *"`, `rerender.batch-size: 100`.

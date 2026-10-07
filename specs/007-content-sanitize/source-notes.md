# Source Notes: 007-content-sanitize

## plan 단계에서 참고할 기술 결정

- 라이브러리: commonmark-java 0.30.0 + GFM 확장, OWASP Java HTML Sanitizer 20260924.2 (docs/12-content-sanitize.md §1 S-3, 01 결정 기록)
- 파이프라인: 파싱 → AST 변환 → `escapeHtml(true)`·`sanitizeUrls(true)` 렌더링 → OWASP 정화 → `content_html` 저장 + `render_version` (12 §2)
- `markdown` 패키지의 `ContentRenderer` 하나로 집중 (12 §2)
- OWASP `HtmlPolicyBuilder` 정책 코드 전문 (12 §4) — CDN 접두어로 img src 검사
- 오류 코드 `400 CONTENT_TOO_COMPLEX` (12 §7-5)
- 미리보기 API `POST /api/markdown/preview {contentMd}` → `{html}`, 1분 60회, 로그인 필요 (12 §7-6)
- `post.render_version integer NOT NULL DEFAULT 1`, `RENDER_VERSION` 상수 + 배치 100개 단위, `content_html`·`excerpt` 갱신 (12 §7-7, §10)
- 코드 강조 highlight.js(브라우저, 우리 서버 제공) (12 §7-2)
- CSP 전문·nosniff·Referrer-Policy (12 §8)
- 검증 결과 52개 테스트(XSS 32 / 정상 13 / 부하·제목 7), 검사기 방법 (12 §9) → 테스트 스위트로 이식
- 렌더링 시점: 발행(05-publish.md §7 ②), 요약 생성 규칙 (10-post-list.md §2-1)
- GIF AST 변환 규칙 추가 (23-image.md §5-2)
- 주의: 12 §8 CSP에는 `connect-src`가 없다. 브라우저→저장소 직접 업로드(Presigned PUT)를 위해 `connect-src`에 저장소 주소가 필요하다 (04-draft-and-image.md §6-1 CSP 행, 검증 H3 `verification/reports/2026-10-06/summary.md`)

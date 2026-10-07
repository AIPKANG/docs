# Research: 본문 렌더링·정화 (007-content-sanitize)

**Phase 0 산출물** · 작성일 2026-10-07 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/12-content-sanitize.md`, `docs/10-post-list.md` §2-1, `docs/23-image.md` §5-2, 헌법, 004 코드

001~004의 결정(SSR, `AccountGuard`, `RedisRateLimiter`, `ErrorResponse`, Testcontainers)은 그대로 쓴다. 사용자 지시: 질문 없이 기본값을 정하고 여기에 남긴다. 005(발행)가 이 렌더러를 쓰므로 순서를 바꿔 007을 먼저 구현한다.

## R-1. 위치와 단일 렌더러 (FR-003)
- **Decision**: `com.team.blog.post.markdown.ContentRenderer` 하나만 HTML을 만든다. 발행(005)·미리보기·다시 렌더링 배치가 모두 이것을 부른다. 결과 `RenderedContent(html, excerpt, imageUrls, renderVersion)`.
- **Rationale**: 12 §2 "한곳에 모음". 본문은 글 모듈 소유라 `post` 아래 패키지로 둔다(헌법 I).

## R-2. 파이프라인 (FR-004, FR-006~FR-011)
- **Decision**: commonmark-java 0.30.0(`Parser` + Tables·Strikethrough·TaskListItems·Autolink 확장) → AST 방문자 변환(제목 +1단계 최대 6, 외부 이미지 → `[이미지] 대체글` 링크, 중첩 깊이 검사) → `HtmlRenderer.escapeHtml(true).sanitizeUrls(true)` + `AttributeProvider`(제목 `id`, 외부 링크 `target`·`rel`, 이미지 `loading`·`decoding`) → OWASP 20260924.2 정책(12 §4 그대로). 체크박스는 TaskListItems 확장이 `disabled`로 그린다.
- 제목 앵커: `h-` + 제목 글자에서 `\p{L}\p{N}_-`만 남기고 공백은 `-`, 영문은 소문자, 100자에서 자름, 비면 `h-section`, 겹치면 `-1`, `-2`.
- 링크 판정: `/`로 시작하고 `//`가 아니면 우리 사이트(같은 탭), `blog.markdown.site-origin`으로 시작해도 우리 사이트, 나머지는 외부.
- 우리 저장소 이미지: `blog.storage.public-base-url` + `/` + 버킷 + `/images/`로 시작하는 주소만(003 `S3ImageStorage.publicUrl` 형식). OWASP 정책의 `img src` 검사도 같은 접두어.
- **Alternatives**: commonmark `HeadingAnchorExtension` — 한글·접두어·길이 규칙을 맞추기 어렵다.

## R-3. 부하 제한 (FR-019)
- **Decision**: 중첩(`BlockQuote`·`ListBlock`) 깊이 20 초과는 파싱 직후 거부. 전체 처리는 렌더링 전용 스레드 풀(최대 4, 대기열 32)에서 `Future.get(1초)`로 시간 제한, 넘으면 `cancel(true)` 후 거부. 둘 다 400 `CONTENT_TOO_COMPLEX`. 설정 `blog.markdown.max-nesting`, `render-timeout`.
- **Rationale**: 12 §7-5. 요청 스레드를 붙잡지 않는다.

## R-4. 요약 (10 §2-1)
- **Decision**: 같은 AST에서 코드 블록·이미지·표를 건너뛰고 글자(`Text`, `Code`, 링크 글자, 글자로 보이는 HTML)를 모아 공백을 하나로 줄이고 앞 200자(단어 중간이면 그 단어 앞)에서 자른다. 정화된 HTML을 다시 파싱하지 않는다(새 의존성 없음). 결과는 글자이며 화면은 `th:text`로만 낸다.
- **Alternatives**: jsoup으로 정화 HTML에서 추출 — 같은 결과를 위해 의존성을 늘린다.

## R-5. 제목 정리 (FR-018)
- **Decision**: `PostTitleRules.clean`: NFC → U+200B~U+200F, U+2060~U+2069, U+FEFF, U+202A~U+202E, 제어 문자(Cc) 제거 → `strip()`. 길이 검사(1~100)는 005 발행 검증이 한다. 004 자동 저장은 원문을 남기므로 이 정리를 쓰지 않는다(발행 때 적용).

## R-6. 미리보기 (FR-020)
- **Decision**: `POST /api/markdown/preview {contentMd}` → `{html}`. 로그인만 필요(인증 전 회원도 볼 수 있음 — 쓰기가 아님), `RedisRateLimiter` 1분 60회(`markdown:preview:member:{id}`) → 429, 10만 자 초과 400 `CONTENT_TOO_LONG`, 복잡하면 400 `CONTENT_TOO_COMPLEX`. 편집 화면에 [미리보기] 전환 버튼, 입력이 0.5초 멈추면 갱신. HTML은 서버가 정화한 결과를 `innerHTML`로 넣는다(같은 출처, CSP로 2차 방어).

## R-7. 렌더링 버전과 다시 렌더링 (FR-021)
- **Decision**: `ContentRenderer.RENDER_VERSION = 1`(V1 기본값과 같음). `RerenderJob`(설정으로 켬, 기본 매일 05:10)·`RerenderService.runBatch()`가 `status='PUBLISHED' AND render_version < 현재`인 글을 100개씩(`id` 순) 다시 렌더링해 `content_html`, `excerpt`, `render_version`만 바꾼다(`edited_at`·`edit_version`·`updated_at`은 그대로). 렌더링 실패 글은 건너뛰고 로그(다음 실행 재시도). 실행 잠금은 004 `JobLock`.
- **Rationale**: 12 §7-7. 버전을 올린 배포 직후 수동 실행도 `runBatch`를 부르면 된다.

## R-8. 보안 헤더 (FR-022)
- **Decision**: 001·003의 CSP·`nosniff`·`Referrer-Policy`를 그대로 쓴다(이미 모든 응답). `img-src`에 저장소 출처가 있어 우리 저장소 이미지만 보인다. 확인 테스트만 더한다.

## R-9. 코드 강조
- **Decision**: 이번에는 highlight.js를 넣지 않는다. `language-xxx` class만 남기면 브라우저 스크립트를 나중에 우리 서버에서 제공해 붙일 수 있고, 스크립트 없이도 코드는 읽힌다(FR-010 충족). 외부 파일을 저장소에 들이는 일은 팀 확인 뒤(U-1).

## R-10. 테스트 (헌법 VI, SC-001~SC-007)
- **Decision**: 12 §9의 52개 사례를 단위 테스트로 옮긴다(XSS 32, 정상 13, 부하·제목 7) + 12 §9 방식의 **위험 HTML 검사기**(태그 허용 목록, `on…`/`style` 속성 금지, `href`/`src`를 엔티티 해제·공백 제거 후 위험 프로토콜 검사)와 검사기 자체 검사(위험 6 / 안전 3). 통합 테스트: 미리보기 API(권한·제한·결과 = 렌더러 결과), 다시 렌더링(버전·시각 불변), 보안 헤더. 브라우저 E2E 알림창 검사는 자동 기반이 없어 quickstart 수동 확인.

## 남은 확인 사항
| # | 내용 | 기본값 |
|---|---|---|
| U-1 | highlight.js를 우리 서버에 둘지 | 보류(R-9) |
| U-2 | 운영 공개 주소가 CDN이면 `blog.storage.public-base-url`만 바꾼다 | 설정 |
| U-3 | GIF 규칙(23 §5-2)은 008에서 AST 변환에 추가 | 008 |

## 구현 메모 (/speckit-implement, 2026-10-07)
- **I-1.** 12 §9의 사례를 그대로 옮겼다: XSS 32(`ContentRendererXssTest`), 정상 문법·링크·이미지·요약 15, 부하 4, 제목 3, 검사기 위험 6/안전 3. 통합 테스트 미리보기 4·다시 렌더링 2·보안 헤더 1. 전체 Gradle 테스트 420개 통과.
- **I-2.** 줄 맨 앞의 `<script>`는 CommonMark HTML 블록이라 같은 문단의 링크 문법까지 글자로 보인다(무해, 원문 그대로 표시).
- **I-3.** 정화기는 내부 링크의 `@`를 `&#64;`로 바꾼다(12 §9-2와 같음, 브라우저는 `@`로 읽음).
- **I-4.** 우리 저장소 이미지 판정은 `ImageStorage.publicUrl("images/")` 접두어로 하고 `..`가 든 주소는 거부한다.
- **I-5.** 미리보기·저장 본문 1MB 제한 필터를 `/api/markdown`에도 걸었다.

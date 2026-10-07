# Source Notes: 024-dark-mode

원문: `docs/45-dark-mode.md` (상태: 초안). spec.md에서 뺀 구현 세부를 `/speckit-plan`용으로 남긴다.

## plan 단계에서 참고할 기술 결정

- 저장: `localStorage` 키 `theme` = `'system' | 'light' | 'dark'` (45 T-3, §2)
- 깜빡임 방지: `<head>`에서 `/js/theme-init.js`(1KB 미만)를 `defer` 없이 CSS보다 먼저 로드, `<html data-theme>`·`data-theme-choice` 설정, `try/catch`로 저장소 차단 대응 (45 T-5, §2)
- CSP `script-src 'self'`(12 §8) 때문에 인라인 스크립트 금지 → 외부 파일 방식
- 시스템 추종: `matchMedia('(prefers-color-scheme: dark)')` 변경 감지 (45 §2)
- JS 꺼짐: CSS `@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {…} }` (45 §3)
- CSS 변수 이름: `--color-bg`, `--color-surface`, `--color-text`, `--color-text-muted`, `--color-border`, `--color-brand`, `--color-brand-fill`, `--color-danger`, `--thumb-empty`, `--color-code-bg`, `--color-focus` (45 §3) — spec의 색 역할 표와 1:1
- 셀렉터: `:root, [data-theme="light"]` / `[data-theme="dark"]` (45 §3)
- 코드 강조: highlight.js `github.css` / `github-dark.css`를 `data-theme`에 맞춰 적용 (45 §4)
- `<meta name="color-scheme" content="light dark">` (45 §4)
- 버튼: 헤더 맨 오른쪽, `aria-label` + 툴팁 (45 T-4)
- SPA도 같은 `theme-init.js`를 `index.html` `<head>`에 넣음 (45 "다른 담당자와 맞출 것")
- ERD 변경 없음 (45 ERD 변경 제안)

## 문서 간 차이 / 미결

- 충돌 없음. 각자 에디터의 다크 테마 지원 여부는 강성찬·김민서 확인 대상 (FR-023 대체 규칙으로 처리)

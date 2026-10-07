# Quickstart: 본문 렌더링·정화 (007) 검증 가이드

1. `./gradlew test --tests '*markdown*'` — XSS 32·정상 13·부하·제목 7 사례와 위험 HTML 검사기(위험 6/안전 3).
2. `./gradlew test --tests '*MarkdownPreviewIT' --tests '*RerenderIT' --tests '*SecurityHeaders*'`.
3. 로컬 화면: `/write/{id}`에서 [미리보기] → `<script>alert(1)</script>`, `[x](javascript:alert(1))`, 표·체크리스트·코드 블록을 입력해 글자로 보이거나 링크가 사라지는지, 알림창이 뜨지 않는지 확인.
4. 다시 렌더링: `UPDATE post SET render_version = 0 WHERE status='PUBLISHED'` 후 `RerenderService.runBatch()` → `render_version = 1`, `edited_at`·`edit_version` 그대로.

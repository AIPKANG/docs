/*
 * 코드 문법 강조(02 기술 표, 024 FR-021): 본문의 ```언어 코드 블록(<code class="language-…">)만 highlight.js로 색을 입힌다.
 * highlight.js는 우리 서버(/webjars/)에서 내려받아 CSP(script-src 'self')를 지키고, 글자는 스스로 이스케이프해 다시 그린다.
 * 언어를 적지 않았거나 모르는 언어는 그대로 둔다(자동 추측 안 함). 스크립트가 없으면 코드는 강조 없이 그대로 읽힌다.
 * 색은 theme.css의 --code-* 역할로 라이트·다크를 따른다.
 */
(function () {
  'use strict';

  function highlight(root) {
    if (!window.hljs || !root) { return; }
    root.querySelectorAll('pre > code[class*="language-"]').forEach(function (el) {
      if (el.dataset.highlighted) { return; }
      var m = /language-([a-z0-9+#-]+)/.exec(el.className);
      if (m && window.hljs.getLanguage(m[1])) { window.hljs.highlightElement(el); }
    });
  }

  if (window.hljs) { window.hljs.configure({ ignoreUnescapedHTML: true }); }
  // 미리보기처럼 나중에 그려지는 곳이 부를 수 있게
  window.blogHighlight = highlight;

  function init() { highlight(document.querySelector('.post-body')); }
  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

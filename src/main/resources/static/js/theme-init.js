/*
 * 테마 먼저 정하기(024 FR-009·FR-010·FR-012): <head>에서 defer 없이 불러 그리기 전에 <html data-theme>을 정한다.
 * 인라인 스크립트가 아니라 CSP(script-src 'self')를 지킨다. 저장소에 접근할 수 없으면 기기 설정만 따른다.
 */
(function () {
  'use strict';
  try {
    var saved = window.localStorage.getItem('blog-theme');
    if (saved === 'light' || saved === 'dark') {
      document.documentElement.setAttribute('data-theme', saved);
    }
  } catch (e) { /* 저장소 차단: 시스템 설정 */ }
})();

/*
 * 테마 전환 버튼(024 FR-004~FR-008): 누를 때마다 시스템 → 라이트 → 다크 → 시스템, 즉시 반영, 이 기기 브라우저에만 저장
 * (로그아웃해도 지우지 않음). "시스템"은 data-theme을 지워 CSS 미디어 쿼리가 기기 설정 변경을 새로 고침 없이 따른다.
 */
(function () {
  'use strict';
  var button = document.getElementById('theme-toggle');
  if (!button) { return; }
  var ORDER = ['system', 'light', 'dark'];
  var LABEL = { system: '테마: 시스템 설정', light: '테마: 라이트', dark: '테마: 다크' };
  var ICON = { system: '🖥', light: '☀', dark: '🌙' };

  function current() {
    var t = document.documentElement.getAttribute('data-theme');
    return t === 'light' || t === 'dark' ? t : 'system';
  }

  function render() {
    var t = current();
    button.textContent = ICON[t];
    button.setAttribute('aria-label', LABEL[t] + ' (눌러서 바꾸기)');
    button.title = LABEL[t];
  }

  button.addEventListener('click', function () {
    var next = ORDER[(ORDER.indexOf(current()) + 1) % ORDER.length];
    if (next === 'system') {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', next);
    }
    try {
      if (next === 'system') { window.localStorage.removeItem('blog-theme'); } else { window.localStorage.setItem('blog-theme', next); }
    } catch (e) { /* 저장소 차단: 이번 화면에만 */ }
    render();
  });

  button.hidden = false;
  render();
})();

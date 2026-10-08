/*
 * 좁은 화면 머리말 메뉴(☰): 버튼이 메뉴를 열고 닫는다. Esc·바깥 누르기·메뉴 안 링크 누르기로 닫힌다.
 * 넓은 화면에서는 CSS가 메뉴를 늘 보여 주므로 버튼은 의미가 없지만 숨겨 둘 필요는 없다(CSS가 숨김).
 */
(function () {
  'use strict';
  var button = document.getElementById('menu-toggle');
  var nav = button && button.closest('.site-nav');
  if (!button || !nav) { return; }
  var mq = window.matchMedia('(max-width: 640px)');

  function set(open) {
    if (open) { nav.setAttribute('data-open', ''); } else { nav.removeAttribute('data-open'); }
    button.setAttribute('aria-expanded', String(open));
    button.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
    button.textContent = open ? '✕' : '☰';
  }

  function sync() {
    button.hidden = !mq.matches;
    if (!mq.matches) { set(false); }
  }

  button.addEventListener('click', function () { set(!nav.hasAttribute('data-open')); });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && nav.hasAttribute('data-open')) { set(false); button.focus(); }
  });
  document.addEventListener('click', function (e) {
    if (nav.hasAttribute('data-open') && !nav.contains(e.target)) { set(false); }
  });
  if (mq.addEventListener) { mq.addEventListener('change', sync); }
  sync();
})();

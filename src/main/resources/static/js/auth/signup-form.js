/*
 * 가입 화면 보조: 서버가 제안한 대안 주소 버튼(data-fill-handle)을 누르면 주소 칸에 채운다.
 * 소셜 화면은 접두어가 고정이라 본문만 채운다(data-prefix-locked).
 */
(function () {
  'use strict';

  function init() {
    var buttons = document.querySelectorAll('button[data-fill-handle]');
    var input = document.getElementById('handle');
    if (!input) { return; }
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener('click', function (e) {
        var value = e.currentTarget.dataset.fillHandle || '';
        input.value = input.dataset.prefixLocked ? value.replace(/^(go|gi)-/, '') : value;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.focus();
      });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

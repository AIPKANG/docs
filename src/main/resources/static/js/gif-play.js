/*
 * 본문 GIF 정지/재생(008 FR-032, 23 §5-2). 렌더러가 만든 <a href="원본.gif"><img src="정지 장면"></a>에서,
 * 누르거나 Enter를 누르면 기본 동작(새 탭)을 막고 img.src를 원본 GIF로, 다시 누르면 정지 장면으로 바꾼다.
 * 스크립트가 없으면 링크 그대로 원본이 새 탭에서 열린다. 인라인 스크립트·허용 목록 변경 없음.
 */
(function () {
  'use strict';

  function bind(root) {
    Array.prototype.forEach.call(root.querySelectorAll('a[href$=".gif"] > img'), function (img) {
      var link = img.parentNode;
      if (link.dataset.gifBound) { return; }
      link.dataset.gifBound = 'true';
      var still = img.getAttribute('src');
      var moving = link.getAttribute('href');
      link.setAttribute('aria-pressed', 'false');
      link.addEventListener('click', function (e) {
        e.preventDefault();
        var playing = link.getAttribute('aria-pressed') === 'true';
        img.setAttribute('src', playing ? still : moving);
        link.setAttribute('aria-pressed', String(!playing));
      });
    });
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll('.post-body'), bind);
    // 미리보기는 내용이 바뀔 때마다 다시 묶는다
    var preview = document.getElementById('preview-body');
    if (preview && window.MutationObserver) {
      new MutationObserver(function () { bind(preview); }).observe(preview, { childList: true, subtree: true });
    }
  }

  window.blogGifPlay = { bind: bind };
  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

/*
 * 조회 기록(016 FR-006, 31 §4-1): 글이 보이는 상태로 1초 지나면 한 번만 POST /api/posts/{id}/views.
 * 탭이 가려지면 기다림을 멈추고 다시 보이면 다시 시작한다. 재전송하지 않는다. 글 번호는 data-view-post-id(인라인 스크립트 없음).
 */
(function () {
  'use strict';

  function init() {
    var article = document.querySelector('[data-view-post-id]');
    if (!article) { return; }
    var postId = article.dataset.viewPostId;
    var sent = false, timer = null;
    function send() {
      if (sent) { return; }
      sent = true;
      var headers = {};
      var token = document.querySelector('meta[name="_csrf"]');
      var header = document.querySelector('meta[name="_csrf_header"]');
      if (token && header) { headers[header.content] = token.content; }
      fetch('/api/posts/' + postId + '/views', { method: 'POST', headers: headers, credentials: 'same-origin', keepalive: true })
        .catch(function () { /* 다시 보내지 않는다 */ });
    }
    function arm() {
      clearTimeout(timer);
      if (!sent && document.visibilityState === 'visible') { timer = setTimeout(send, 1000); }
    }
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'visible') { arm(); } else { clearTimeout(timer); }
    });
    arm();
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

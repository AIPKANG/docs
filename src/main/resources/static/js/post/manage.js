/*
 * 내 글 목록의 공개 범위 바꾸기(006 R-5). PATCH /api/posts/{id}/visibility 후 배지·버튼 글자를 바꾼다.
 */
(function () {
  'use strict';

  function csrfHeaders() {
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function init() {
    var notice = document.getElementById('manage-notice');
    Array.prototype.forEach.call(document.querySelectorAll('.visibility-toggle'), function (button) {
      button.addEventListener('click', function () {
        var to = button.dataset.visibility === 'PRIVATE' ? 'PUBLIC' : 'PRIVATE';
        button.disabled = true;
        fetch('/api/posts/' + button.dataset.postId + '/visibility', {
          method: 'PATCH', headers: csrfHeaders(), credentials: 'same-origin', body: JSON.stringify({ visibility: to })
        }).then(function (res) { return res.json().then(function (d) { return { ok: res.ok, data: d }; }); })
          .then(function (r) {
            button.disabled = false;
            if (!r.ok) { notice.textContent = r.data.message || '공개 범위를 바꾸지 못했어요.'; notice.hidden = false; return; }
            button.dataset.visibility = r.data.visibility;
            button.textContent = r.data.visibility === 'PRIVATE' ? '전체 공개로' : '나만 보기로';
            var badge = button.parentNode.querySelector('[data-visibility-badge]');
            if (badge) { badge.textContent = r.data.visibility === 'PRIVATE' ? '🔒 나만 보기' : '🌐 전체 공개'; }
          }, function () {
            button.disabled = false;
            notice.textContent = '연결되지 않아 공개 범위를 바꾸지 못했어요.';
            notice.hidden = false;
          });
      });
    });
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

/*
 * 팔로우 버튼(018 FR-007): 누르는 즉시 "팔로우" ↔ "팔로잉 ✓"로 바꾸고 PUT/DELETE를 보낸다. 실패하면 되돌리고
 * "잠시 후 다시 시도해 주세요". 팔로우 중인 버튼은 마우스를 올리거나 초점을 받으면 "언팔로우". 스크립트가 없으면 폼이 그대로 동작한다.
 */
(function () {
  'use strict';

  function csrf() {
    var t = document.querySelector('meta[name="_csrf"]'), h = document.querySelector('meta[name="_csrf_header"]');
    var headers = {};
    if (t && h) { headers[h.content] = t.content; }
    return headers;
  }

  function label(btn, hover) {
    var following = btn.dataset.following === 'true';
    btn.textContent = following ? (hover ? '언팔로우' : '팔로잉 ✓') : '팔로우';
    btn.setAttribute('aria-pressed', String(following));
  }

  function message(btn, text) {
    var scope = btn.closest('main') || document;
    var box = scope.querySelector('.follow-message');
    if (!box) { return; }
    box.textContent = text;
    box.hidden = !text;
  }

  function sync(handle, following) {
    document.querySelectorAll('.follow-button[data-follow-handle]').forEach(function (b) {
      if (b.dataset.followHandle === handle) {
        b.dataset.following = String(following);
        var input = b.form && b.form.querySelector('input[name="following"]');
        if (input) { input.value = String(!following); }
        label(b, false);
      }
    });
  }

  document.querySelectorAll('.follow-button[data-follow-handle]').forEach(function (btn) {
    ['mouseenter', 'focus'].forEach(function (ev) { btn.addEventListener(ev, function () { label(btn, true); }); });
    ['mouseleave', 'blur'].forEach(function (ev) { btn.addEventListener(ev, function () { label(btn, false); }); });
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      if (btn.dataset.busy === 'true') { return; }
      var handle = btn.dataset.followHandle;
      var was = btn.dataset.following === 'true';
      btn.dataset.busy = 'true';
      sync(handle, !was);
      message(btn, '');
      fetch('/api/members/' + encodeURIComponent(handle) + '/follow', {
        method: was ? 'DELETE' : 'PUT', credentials: 'same-origin', headers: csrf()
      }).then(function (r) {
        if (r.status === 401) { location.href = '/login?redirect=' + encodeURIComponent(location.pathname); return; }
        if (!r.ok) { throw new Error(); }
      }).catch(function () {
        sync(handle, was);
        message(btn, '잠시 후 다시 시도해 주세요');
      }).then(function () { btn.dataset.busy = 'false'; });
    });
  });
})();

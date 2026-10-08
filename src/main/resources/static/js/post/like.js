/*
 * 좋아요 버튼(015 FR-013~FR-019). 누르면 ♡/♥와 숫자를 바로 바꾸고, 0.3초 동안 더 누르지 않으면 마지막 상태 하나만 보낸다
 * (PUT 좋아요 / DELETE 취소). 실패하면 되돌리고 "좋아요를 반영하지 못했어요". 최종 숫자는 서버 응답으로 맞춘다.
 * 비회원은 로그인 안내, 인증 전 회원은 인증 안내. 스크립트가 없으면 폼이 그대로 제출된다.
 */
(function () {
  'use strict';

  var DEBOUNCE_MS = 300;

  function format(n) {
    if (n < 10000) { return n.toLocaleString('ko-KR'); }
    var tenths = Math.floor(n / 1000), whole = Math.floor(tenths / 10), frac = tenths % 10;
    return whole.toLocaleString('ko-KR') + (frac ? '.' + frac : '') + '만';
  }

  function csrfHeaders() {
    var headers = { 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function init() {
    var form = document.querySelector('.like-form');
    if (!form) { return; }
    var button = form.querySelector('.like-button');
    var message = document.querySelector('.like-message');
    var postId = form.dataset.postId;
    var serverLiked = button.getAttribute('aria-pressed') === 'true';
    var serverCount = Number(button.dataset.count);
    var liked = serverLiked, count = serverCount, timer = null, sending = false;

    function paint() {
      button.setAttribute('aria-pressed', String(liked));
      button.setAttribute('aria-label', (liked ? '좋아요 취소 (' : '좋아요 (') + count + ')');
      button.querySelector('.like-icon').textContent = liked ? '♥' : '♡';
      button.querySelector('.like-number').textContent = format(count);
      form.querySelector('input[name="liked"]').value = String(!liked);
    }

    function say(nodes) {
      message.textContent = '';
      nodes.forEach(function (n) { message.appendChild(typeof n === 'string' ? document.createTextNode(n) : n); });
      message.hidden = false;
    }

    function send() {
      if (sending) { timer = setTimeout(send, DEBOUNCE_MS); return; }
      if (liked === serverLiked) { return; }
      sending = true;
      var want = liked;
      fetch('/api/posts/' + postId + '/like', { method: want ? 'PUT' : 'DELETE', headers: csrfHeaders(), credentials: 'same-origin' })
        .then(function (res) {
          return res.json().catch(function () { return {}; }).then(function (data) {
            if (!res.ok) { var e = new Error(String(res.status)); e.data = data; e.status = res.status; throw e; }
            return data;
          });
        })
        .then(function (data) {
          serverLiked = data.liked; serverCount = data.likeCount;
          if (liked === want) { count = serverCount; paint(); }
        })
        .catch(function (err) {
          liked = serverLiked; count = serverCount; paint();
          if (err.status === 403) { say(['이메일 인증을 마치면 좋아요를 누를 수 있어요.']); }
          else { say(['좋아요를 반영하지 못했어요']); }
        })
        .then(function () { sending = false; });
    }

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      if (form.dataset.guest === 'true') {
        var a = document.createElement('a'); a.href = form.dataset.login; a.textContent = '로그인';
        say(['로그인하고 좋아요를 눌러 보세요 ', a]);
        return;
      }
      liked = !liked;
      count = Math.max(0, count + (liked ? 1 : -1));
      paint();
      clearTimeout(timer);
      timer = setTimeout(send, DEBOUNCE_MS);
    });
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

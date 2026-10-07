/*
 * 가입 화면 블로그 주소 칸 (research R-4, contracts/web-routes.md §2).
 * 마크업(001 가입 화면 T134·T163):
 *   <input id="email" ...>
 *   <input id="handle" name="handle" data-handle-field data-email-source="#email"
 *          inputmode="latin" autocapitalize="off" autocomplete="off" spellcheck="false" lang="en"
 *          [data-prefix-locked="go-"]>   소셜 화면은 접두어를 고정 글자로 두고 본문만 편집
 * - 이메일 입력이 0.5초 멈추면 POST /api/handles/suggestion(CSRF 메타 태그 사용) 결과로 주소 칸을 채운다.
 * - 사용자가 주소 칸을 한 번이라도 직접 고치면(handleTouched) 그 뒤로는 자동 채움을 멈춘다.
 * - 입력 중 대문자는 소문자로, '-'와 허용 외 문자는 들어가지 않게 막는다.
 * 이메일은 요청 본문으로만 보낸다(URL에 싣지 않음).
 */
(function () {
  'use strict';

  var DEBOUNCE_MS = 500;
  var NOT_ALLOWED = /[^a-z0-9_]/g;

  function csrfHeaders() {
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.getAttribute('content')] = token.getAttribute('content'); }
    return headers;
  }

  function sanitize(value) {
    return value.toLowerCase().replace(NOT_ALLOWED, '');
  }

  function bind(handleInput) {
    var prefix = handleInput.dataset.prefixLocked || '';
    var emailInput = document.querySelector(handleInput.dataset.emailSource || '');
    var handleTouched = false;
    var programmatic = false;
    var timer = null;
    var controller = null;

    handleInput.setAttribute('inputmode', 'latin');
    handleInput.setAttribute('autocapitalize', 'off');
    handleInput.setAttribute('lang', 'en');

    handleInput.addEventListener('input', function () {
      var start = handleInput.selectionStart;
      var before = handleInput.value;
      var cleaned = sanitize(before);
      if (cleaned !== before) {
        handleInput.value = cleaned;
        if (start !== null) {
          var pos = Math.max(0, start - (before.length - cleaned.length));
          handleInput.setSelectionRange(pos, pos);
        }
      }
      if (!programmatic) { handleTouched = true; }
    });

    function fill(handle) {
      if (handleTouched || !handle) { return; }
      var body = prefix && handle.indexOf(prefix) === 0 ? handle.substring(prefix.length) : handle;
      programmatic = true;
      handleInput.value = body;
      handleInput.dispatchEvent(new Event('input', { bubbles: true }));
      programmatic = false;
    }

    function suggest() {
      if (handleTouched || !emailInput) { return; }
      var email = emailInput.value.trim();
      if (!email) { return; }
      if (controller) { controller.abort(); }
      controller = new AbortController();
      fetch('/api/handles/suggestion', {
        method: 'POST',
        headers: csrfHeaders(),
        credentials: 'same-origin',
        body: JSON.stringify({ email: email }),
        signal: controller.signal
      }).then(function (res) {
        return res.ok ? res.json() : null;
      }).then(function (body) {
        if (body && body.handle) { fill(body.handle); }
      }).catch(function () { /* 미리 채우기 실패는 무시: 사용자가 직접 입력한다 */ });
    }

    if (emailInput) {
      emailInput.addEventListener('input', function () {
        if (handleTouched) { return; }
        if (timer) { clearTimeout(timer); }
        timer = setTimeout(suggest, DEBOUNCE_MS);
      });
    }
  }

  function init() {
    var inputs = document.querySelectorAll('input[data-handle-field]');
    for (var i = 0; i < inputs.length; i++) { bind(inputs[i]); }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

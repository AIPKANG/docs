/*
 * 블로그 주소·닉네임 칸 공용 실시간 확인 (contracts/web-routes.md §1).
 * 마크업: <input data-availability="handle|nickname" data-availability-target="#결과요소id">
 * 입력이 0.5초 멈추면 API를 부르고, 이전 요청은 AbortController로 취소한다.
 * 결과는 textContent로만 넣는다(이스케이프). 429는 "사용 중"과 구분해 "잠시 후 다시 확인해 주세요"로 보여준다.
 */
(function () {
  'use strict';

  var DEBOUNCE_MS = 500;

  var ENDPOINTS = {
    handle: function (v) { return '/api/handles/availability?handle=' + encodeURIComponent(v); },
    nickname: function (v) { return '/api/nicknames/availability?nickname=' + encodeURIComponent(v); }
  };

  var MESSAGES = {
    OK_handle: '사용할 수 있어요',
    OK_nickname: '사용할 수 있어요',
    RATE_LIMITED: '잠시 후 다시 확인해 주세요',
    ERROR: '지금은 확인할 수 없어요. 가입할 때 다시 확인해요',
    // 블로그 주소 (reason)
    INVALID_FORMAT: '영문 소문자·숫자·_로 3~36자까지 쓸 수 있어요',
    RESERVED: '사용할 수 없는 주소예요',
    BANNED_WORD: '사용할 수 없는 단어가 들어 있어요',
    DUPLICATE: '이미 사용 중인 주소예요',
    // 닉네임 (code)
    NICKNAME_INVALID_FORMAT: '한글·영문·숫자로 2~10자까지 쓸 수 있어요 (공백·특수문자 불가)',
    NICKNAME_LETTER_REQUIRED: '한글이나 영문을 1자 이상 넣어 주세요',
    NICKNAME_RESERVED: '사용할 수 없는 닉네임이에요',
    NICKNAME_BANNED_WORD: '사용할 수 없는 단어가 들어 있어요',
    NICKNAME_DUPLICATE: '이미 사용 중인 닉네임이에요'
  };

  function render(target, input, text, ok, suggestion) {
    if (!target) { return; }
    while (target.firstChild) { target.removeChild(target.firstChild); }
    target.dataset.state = ok ? 'ok' : 'error';
    var span = document.createElement('span');
    span.textContent = text;
    target.appendChild(span);
    if (suggestion) {
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'availability-suggestion';
      button.textContent = suggestion + '는 어떠세요?';
      button.addEventListener('click', function () {
        input.value = input.dataset.prefixLocked ? suggestion.replace(/^(go|gi)-/, '') : suggestion;
        input.dispatchEvent(new Event('input', { bubbles: true }));
      });
      target.appendChild(document.createTextNode(' '));
      target.appendChild(button);
    }
  }

  function bind(input) {
    var kind = input.dataset.availability;
    var endpoint = ENDPOINTS[kind];
    if (!endpoint) { return; }
    var target = document.querySelector(input.dataset.availabilityTarget || '');
    var timer = null;
    var controller = null;

    function valueToCheck() {
      var prefix = input.dataset.prefixLocked || '';
      return prefix + input.value.trim();
    }

    function check() {
      var value = valueToCheck();
      if (controller) { controller.abort(); }
      if (!input.value.trim()) { render(target, input, '', true, null); return; }
      controller = new AbortController();
      fetch(endpoint(value), {
        headers: { 'Accept': 'application/json' },
        credentials: 'same-origin',
        signal: controller.signal
      }).then(function (res) {
        if (res.status === 429) { render(target, input, MESSAGES.RATE_LIMITED, false, null); return null; }
        if (!res.ok) { render(target, input, MESSAGES.ERROR, false, null); return null; }
        return res.json();
      }).then(function (body) {
        if (!body) { return; }
        if (body.available) {
          render(target, input, MESSAGES['OK_' + kind], true, null);
        } else {
          var key = kind === 'handle' ? body.reason : body.code;
          render(target, input, MESSAGES[key] || MESSAGES.ERROR, false, body.suggestion || null);
        }
      }).catch(function (err) {
        if (err && err.name === 'AbortError') { return; }
        render(target, input, MESSAGES.ERROR, false, null);
      });
    }

    input.addEventListener('input', function () {
      if (timer) { clearTimeout(timer); }
      timer = setTimeout(check, DEBOUNCE_MS);
    });
    input.addEventListener('availability:check', check);
  }

  function init() {
    var inputs = document.querySelectorAll('input[data-availability]');
    for (var i = 0; i < inputs.length; i++) { bind(inputs[i]); }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

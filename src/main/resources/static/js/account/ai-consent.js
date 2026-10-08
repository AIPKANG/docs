/* 설정의 AI 태그 추천 동의 상태와 철회(021 FR-010). 철회하면 다음 사용 때 다시 묻는다. */
(function () {
  'use strict';
  var state = document.getElementById('ai-consent-state');
  var button = document.getElementById('ai-consent-withdraw');
  if (!state || !button) { return; }

  function show(consented) {
    state.textContent = consented ? '외부 AI 전송에 동의했어요. 철회하면 다음에 추천을 받을 때 다시 물어봐요.'
      : '아직 동의하지 않았어요. 글쓰기 화면에서 처음 추천을 받을 때 물어봐요.';
    button.hidden = !consented;
  }

  fetch('/api/me/ai-consent', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (s) { if (s) { show(s.consented); } else { state.textContent = ''; } })
    .catch(function () { state.textContent = ''; });

  button.addEventListener('click', function () {
    var headers = {};
    var t = document.querySelector('meta[name="_csrf"]'), n = document.querySelector('meta[name="_csrf_header"]');
    if (t && n) { headers[n.content] = t.content; }
    fetch('/api/me/ai-consent', { method: 'DELETE', credentials: 'same-origin', headers: headers })
      .then(function (r) { if (r.ok) { show(false); } });
  });
})();

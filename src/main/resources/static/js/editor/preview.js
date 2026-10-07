/*
 * 미리보기(007 FR-020, 12 §7-6). [미리보기]를 누르면 영역을 열고, 열린 동안 입력이 0.5초 멈출 때마다
 * POST /api/markdown/preview로 서버 렌더러 결과를 받아 넣는다. 결과는 서버가 허용 목록으로 정화한 HTML이다.
 */
(function () {
  'use strict';

  var DELAY_MS = 500;

  function csrfHeaders() {
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function init() {
    var toggle = document.getElementById('preview-toggle');
    var panel = document.getElementById('preview-panel');
    var body = document.getElementById('preview-body');
    var status = document.getElementById('preview-status');
    var content = document.getElementById('post-content');
    if (!toggle || !panel || !content) { return; }
    var timer = null, controller = null;

    function refresh() {
      if (panel.hidden) { return; }
      if (controller) { controller.abort(); }
      controller = new AbortController();
      status.textContent = '미리보기를 만드는 중…';
      fetch('/api/markdown/preview', { method: 'POST', headers: csrfHeaders(), credentials: 'same-origin',
        body: JSON.stringify({ contentMd: content.value }), signal: controller.signal })
        .then(function (res) { return res.json().then(function (data) { return { ok: res.ok, data: data }; }); })
        .then(function (r) {
          if (!r.ok) { status.textContent = r.data.message || '미리보기를 만들지 못했어요'; return; }
          body.innerHTML = r.data.html; // 서버가 정화한 HTML(같은 출처, CSP 2차 방어)
          status.textContent = '';
        }, function (e) { if (e.name !== 'AbortError') { status.textContent = '연결되지 않아 미리보기를 만들지 못했어요'; } });
    }

    toggle.addEventListener('click', function () {
      panel.hidden = !panel.hidden;
      toggle.setAttribute('aria-expanded', String(!panel.hidden));
      toggle.textContent = panel.hidden ? '미리보기' : '미리보기 닫기';
      refresh();
    });
    content.addEventListener('input', function () {
      if (panel.hidden) { return; }
      clearTimeout(timer);
      timer = setTimeout(refresh, DELAY_MS);
    });
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

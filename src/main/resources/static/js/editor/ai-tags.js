/*
 * AI 태그 추천(021, 34 §9): [AI 태그 추천]·[다시 추천]을 누를 때만 요청한다(글쓰기·자동 저장·발행 중 자동 요청 없음).
 * 후보는 눌러야 태그로 더한다. 동의 전이면 동의 창, 공개 글이 아니면 안내만. 자체 AI로 처리 중이면 오래 걸린다고 알린다.
 * 실패해도 글쓰기에는 영향이 없다. 글자는 textContent로만 넣는다.
 */
(function () {
  'use strict';

  function headers() {
    var h = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var t = document.querySelector('meta[name="_csrf"]'), n = document.querySelector('meta[name="_csrf_header"]');
    if (t && n) { h[n.content] = t.content; }
    return h;
  }

  function init() {
    var box = document.getElementById('ai-tags');
    var editor = window.blogEditor;
    if (!box || !editor) { return; }
    var button = document.getElementById('ai-suggest');
    var refresh = document.getElementById('ai-refresh');
    var result = document.getElementById('ai-result');
    var candidates = document.getElementById('ai-candidates');
    var status = document.getElementById('ai-status');
    var remaining = document.getElementById('ai-remaining');
    var publicOnly = document.getElementById('ai-public-only');
    var truncated = document.getElementById('ai-truncated');
    var dialog = document.getElementById('ai-consent-dialog');
    var consented = false;
    var busy = false;

    fetch('/api/me/ai-consent', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (s) { if (s && s.enabled) { consented = s.consented; box.hidden = false; sync(); } })
      .catch(function () {});

    function sync() {
      var tagsApi = window.blogEditorTags;
      var isPublic = !tagsApi || tagsApi.visibility() === 'PUBLIC';
      button.hidden = !isPublic;
      refresh.disabled = !isPublic;
      publicOnly.hidden = isPublic;
    }
    document.querySelectorAll('#publish-panel input[name="visibility"]').forEach(function (r) { r.addEventListener('change', sync); });

    function show(body) {
      candidates.textContent = '';
      (body.tags || []).forEach(function (tag) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'tag-chip ai-candidate';
        b.textContent = '+ ' + tag;
        b.addEventListener('click', function () {
          if (window.blogEditorTags) { window.blogEditorTags.add(tag); }
          b.remove();
        });
        candidates.appendChild(b);
      });
      result.hidden = false;
      truncated.hidden = !body.truncated;
      remaining.textContent = '(오늘 ' + body.remainingToday + '회 남음)';
      status.textContent = body.message || (body.cached ? '저장된 추천을 다시 보여 드렸어요' : '');
    }

    function request(isRefresh) {
      if (busy) { return; }
      if (!consented) { dialog.showModal(); dialog.dataset.refresh = String(isRefresh); return; }
      busy = true;
      button.disabled = true;
      refresh.disabled = true;
      status.textContent = '추천 중…';
      var slow = setTimeout(function () { status.textContent = '자체 AI로 추천 중이라 조금 걸려요'; }, 3000);
      var c = editor.content();
      var tagsApi = window.blogEditorTags;
      fetch('/api/posts/' + editor.state.postId + '/tag-suggestions', {
        method: 'POST', credentials: 'same-origin', headers: headers(),
        body: JSON.stringify({ title: c.title, contentMd: c.contentMd, currentTags: tagsApi ? tagsApi.list() : [],
          visibility: tagsApi ? tagsApi.visibility() : 'PUBLIC', refresh: isRefresh })
      }).then(function (r) {
        return r.json().catch(function () { return {}; }).then(function (body) { return { status: r.status, body: body }; });
      }).then(function (res) {
        if (res.status === 200) { show(res.body); return; }
        if (res.status === 409 && res.body.code === 'AI_CONSENT_REQUIRED') { consented = false; dialog.showModal(); status.textContent = ''; return; }
        status.textContent = res.body.message || '지금은 추천할 수 없어요';
      }).catch(function () {
        status.textContent = '지금은 추천할 수 없어요';
      }).then(function () {
        clearTimeout(slow);
        busy = false;
        button.disabled = false;
        sync();
      });
    }

    button.addEventListener('click', function () { request(false); });
    refresh.addEventListener('click', function () { request(true); });
    document.getElementById('ai-consent-cancel').addEventListener('click', function () { dialog.close(); });
    document.getElementById('ai-consent-agree').addEventListener('click', function () {
      fetch('/api/me/ai-consent', { method: 'POST', credentials: 'same-origin', headers: headers() })
        .then(function (r) {
          dialog.close();
          if (r.ok) { consented = true; request(dialog.dataset.refresh === 'true'); }
          else { status.textContent = '지금은 추천할 수 없어요'; }
        });
    });
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

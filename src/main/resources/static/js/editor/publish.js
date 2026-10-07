/*
 * 발행 설정과 [발행](005 FR-018·FR-019, 05 §6, 22 §3).
 * - 태그: Enter나 쉼표로 칩 추가(화면 정규화는 안내용, 서버가 다시 검사), ×·빈 칸 Backspace로 삭제.
 * - [발행]: 버튼 비활성 + "발행 중…", 누를 때마다 새 Idempotency-Key. 409 IN_PROGRESS면 1초 뒤 같은 키로 재시도,
 *   409 EDIT_CONFLICT면 004 비교 창, 400이면 칸별 문구, 200이면 이 기기 임시 데이터를 지우고 글 주소로 이동.
 */
(function () {
  'use strict';

  function csrfHeaders(key) {
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json', 'Idempotency-Key': key };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function newKey() {
    if (window.crypto && typeof crypto.randomUUID === 'function') { return crypto.randomUUID(); }
    return 'k' + Date.now().toString(36) + Math.random().toString(36).slice(2);
  }

  /* 22 §2 ③~⑦의 화면 미리 보여주기(안내용) */
  function shape(raw) {
    var s = (raw || '').normalize('NFKC').replace(/[​-‏⁠-⁩﻿‪-‮]/g, '').trim();
    s = s.replace(/^#+/, '').trim().toLowerCase().replace(/\s+/g, '-').replace(/-{2,}/g, '-').replace(/^-+|-+$/g, '');
    return s;
  }

  function init() {
    var editor = window.blogEditor;
    var panel = document.getElementById('publish-panel');
    if (!editor || !panel) { return; }
    var openButton = document.getElementById('publish-open');
    var submit = document.getElementById('publish-submit');
    var chips = document.getElementById('tag-chips');
    var input = document.getElementById('tag-input');
    var count = document.getElementById('tag-count');
    var errors = document.getElementById('publish-errors');
    var tags = (editor.state.tags || []).slice();
    var maxTags = editor.state.maxTags || 10;

    function renderChips(errorIndexes) {
      chips.textContent = '';
      tags.forEach(function (tag, i) {
        var chip = document.createElement('span');
        chip.className = 'tag-chip';
        if (errorIndexes && errorIndexes[i]) { chip.dataset.error = 'true'; }
        chip.appendChild(document.createTextNode('#' + tag + ' '));
        var remove = document.createElement('button');
        remove.type = 'button';
        remove.textContent = '×';
        remove.setAttribute('aria-label', tag + ' 태그 빼기');
        remove.addEventListener('click', function () { tags.splice(i, 1); renderChips(); });
        chip.appendChild(remove);
        chips.appendChild(chip);
      });
      count.textContent = tags.length + ' / ' + maxTags + '   Enter나 쉼표로 추가';
    }

    function addTag() {
      var parts = input.value.split(',');
      parts.forEach(function (p) {
        var t = shape(p);
        if (t && tags.indexOf(t) < 0) { tags.push(t); }
      });
      input.value = '';
      renderChips();
    }

    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); addTag(); }
      else if (e.key === 'Backspace' && input.value === '' && tags.length) { tags.pop(); renderChips(); }
    });
    input.addEventListener('blur', function () { if (input.value.trim()) { addTag(); } });

    openButton.addEventListener('click', function () {
      if (editor.isConflict()) { editor.openCompare(); return; }
      panel.hidden = !panel.hidden;
      openButton.setAttribute('aria-expanded', String(!panel.hidden));
    });
    document.getElementById('publish-cancel').addEventListener('click', function () {
      panel.hidden = true;
      openButton.setAttribute('aria-expanded', 'false');
    });

    function showErrors(list) {
      errors.textContent = '';
      var tagErrors = {};
      (list || []).forEach(function (err) {
        var li = document.createElement('li');
        li.textContent = err.message || err.code;
        errors.appendChild(li);
        var m = /^tags\[(\d+)\]$/.exec(err.field || '');
        if (m) { tagErrors[parseInt(m[1], 10)] = true; }
      });
      renderChips(tagErrors);
    }

    function done() {
      submit.disabled = false;
      submit.textContent = '발행';
    }

    function send(key, body) {
      return fetch('/api/posts/' + editor.state.postId + '/publish', {
        method: 'POST', headers: csrfHeaders(key), credentials: 'same-origin', body: body
      }).then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (data) { return { status: res.status, data: data }; });
      }).then(function (r) {
        if (r.status === 200) {
          editor.published().then(function () { window.location.assign(r.data.url); });
          return;
        }
        if (r.status === 409 && r.data.code === 'IN_PROGRESS') {
          setTimeout(function () { send(key, body); }, 1000);
          return;
        }
        done();
        if (r.status === 409 && r.data.server) { panel.hidden = true; editor.conflict(r.data.server); return; }
        if (r.data.errors) { showErrors(r.data.errors); return; }
        showErrors([{ message: r.data.message || '발행하지 못했어요. 잠시 뒤 다시 시도해 주세요.' }]);
      }, function () {
        done();
        showErrors([{ message: '연결되지 않아 발행하지 못했어요. 내용은 이 기기에 남아 있어요.' }]);
      });
    }

    submit.addEventListener('click', function () {
      if (submit.disabled) { return; }
      if (input.value.trim()) { addTag(); }
      submit.disabled = true;
      submit.textContent = '발행 중…';
      errors.textContent = '';
      var checked = panel.querySelector('input[name="visibility"]:checked');
      var c = editor.content();
      var body = JSON.stringify({ title: c.title, contentMd: c.contentMd, tags: tags,
        visibility: checked ? checked.value : 'PUBLIC', baseVersion: editor.baseVersion() });
      send(newKey(), body);
    });

    renderChips();
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

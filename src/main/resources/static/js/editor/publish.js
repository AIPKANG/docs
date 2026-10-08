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
        chip.tabIndex = 0;
        chip.draggable = true;
        chip.setAttribute('aria-label', tag + ' 태그, Alt와 방향키로 순서 바꾸기');
        chip.addEventListener('dragstart', function (e) { e.dataTransfer.setData('text/plain', String(i)); });
        chip.addEventListener('dragover', function (e) { e.preventDefault(); });
        chip.addEventListener('drop', function (e) {
          e.preventDefault();
          var from = parseInt(e.dataTransfer.getData('text/plain'), 10);
          if (isNaN(from) || from === i) { return; }
          var moved = tags.splice(from, 1)[0];
          tags.splice(i, 0, moved);
          renderChips();
        });
        chip.addEventListener('keydown', function (e) {
          if (!e.altKey || (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight')) { return; }
          e.preventDefault();
          var to = e.key === 'ArrowLeft' ? i - 1 : i + 1;
          if (to < 0 || to >= tags.length) { return; }
          var t = tags[i]; tags[i] = tags[to]; tags[to] = t;
          renderChips();
          chips.children[to].focus();
        });
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

    // 021 AI 태그 추천이 칩을 읽고 더할 수 있게(누른 것만 더한다)
    window.blogEditorTags = {
      list: function () { return tags.slice(); },
      max: maxTags,
      visibility: function () { var r = panel.querySelector('input[name="visibility"]:checked'); return r ? r.value : 'PUBLIC'; },
      add: function (t) { if (t && tags.indexOf(t) < 0 && tags.length < maxTags) { tags.push(t); renderChips(); } }
    };

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
      if (composing) { return; }
      if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); addTag(); }
      else if (e.key === 'Backspace' && input.value === '' && tags.length) { tags.pop(); renderChips(); }
    });
    input.addEventListener('blur', function () { setTimeout(function () { if (input.value.trim()) { addTag(); } closeSuggest(); }, 150); });

    /* 013 자동완성: 0.3초 멈추면, 한글 조합 중에는 부르지 않는다. 내 태그 먼저. */
    var suggestBox = document.createElement('ul');
    suggestBox.className = 'tag-suggest';
    suggestBox.setAttribute('role', 'listbox');
    suggestBox.hidden = true;
    input.parentNode.insertBefore(suggestBox, input.nextSibling);
    var composing = false, suggestTimer = null;
    function closeSuggest() { suggestBox.hidden = true; suggestBox.textContent = ''; }
    input.addEventListener('compositionstart', function () { composing = true; });
    input.addEventListener('compositionend', function () { composing = false; scheduleSuggest(); });
    input.addEventListener('input', function () { if (!composing) { scheduleSuggest(); } });
    function scheduleSuggest() {
      clearTimeout(suggestTimer);
      suggestTimer = setTimeout(function () {
        var q = shape(input.value.split(',').pop());
        if (!q) { closeSuggest(); return; }
        fetch('/api/tags/suggest?q=' + encodeURIComponent(q), { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
          .then(function (res) { return res.ok ? res.json() : []; })
          .then(function (items) {
            suggestBox.textContent = '';
            items.forEach(function (item) {
              var li = document.createElement('li');
              li.setAttribute('role', 'option');
              li.textContent = '#' + item.name + ' ' + item.postCount + (item.mine ? ' · 내 태그' : '');
              li.addEventListener('mousedown', function (e) {
                e.preventDefault();
                if (tags.indexOf(item.name) < 0) { tags.push(item.name); }
                input.value = '';
                renderChips();
                closeSuggest();
              });
              suggestBox.appendChild(li);
            });
            suggestBox.hidden = items.length === 0;
          }).catch(closeSuggest);
      }, 300);
    }

    /* 008 FR-029·FR-030: 대체글이 빈 사진 안내와 사진별 입력칸(본문 ![대체글](주소)에 반영, 발행은 막지 않음) */
    var altSection = document.getElementById('alt-section');
    var altSummary = document.getElementById('alt-summary');
    var altFields = document.getElementById('alt-fields');
    var contentInput = document.getElementById('post-content');
    function renderAlts() {
      if (!altSection) { return; }
      var re = /!\[\]\(([^)\s]+)\)/g, m, urls = [];
      while ((m = re.exec(contentInput.value)) !== null) {
        if (m[1].indexOf('local:') !== 0 && urls.indexOf(m[1]) < 0) { urls.push(m[1]); }
      }
      altFields.textContent = '';
      altSection.hidden = urls.length === 0;
      altSummary.textContent = '대체글이 없는 사진이 ' + urls.length + '장 있어요. 화면 낭독기를 쓰는 분을 위해 사진을 설명해 주세요(없어도 발행돼요).';
      urls.forEach(function (url, i) {
        var row = document.createElement('div');
        row.className = 'alt-field';
        var img = document.createElement('img');
        img.src = url; img.alt = ''; img.width = 48; img.height = 48; img.style.objectFit = 'cover';
        var input = document.createElement('input');
        input.type = 'text';
        input.setAttribute('aria-label', '사진 ' + (i + 1) + ' 대체글');
        var hint = document.createElement('span');
        hint.className = 'help';
        input.addEventListener('input', function () {
          hint.textContent = input.value.length > 125 ? '125자가 넘어요. 짧게 줄이면 듣기 편해요.' : '';
        });
        input.addEventListener('change', function () {
          var alt = input.value.replace(/[\[\]\n]/g, ' ').trim();
          if (!alt) { return; }
          contentInput.value = contentInput.value.split('![](' + url + ')').join('![' + alt + '](' + url + ')');
          contentInput.dispatchEvent(new Event('input', { bubbles: true }));
        });
        row.appendChild(img); row.appendChild(input); row.appendChild(hint);
        altFields.appendChild(row);
      });
    }

    openButton.addEventListener('click', function () {
      if (editor.isConflict()) { editor.openCompare(); return; }
      panel.hidden = !panel.hidden;
      openButton.setAttribute('aria-expanded', String(!panel.hidden));
      if (!panel.hidden) { renderAlts(); }
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

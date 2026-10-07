/*
 * 편집 화면 묶기(004, contracts/web-routes.md §3). 서버 상태는 #editor[data-state] JSON.
 * - 열 때(FR-019): 로컬에 못 보낸 내용이 있으면 출발 버전이 서버와 같으면 이어 쓰고, 다르면 비교 창을 바로 띄운다.
 * - 충돌(FR-018): 배너와 상태 줄, [비교하기]·[저장]을 누르면 비교 창.
 * - 비교 창(FR-020·FR-021): 줄·단어 강조(−/+ 기호와 색), 긴 같은 구간 접기, 이전·다음 차이, 세 선택지와 닫기.
 * 사용자 글은 모두 textContent로만 넣는다(이스케이프).
 */
(function () {
  'use strict';

  var FOLD_MIN = 6, FOLD_KEEP = 2;

  function hhmm(iso) {
    if (!iso) { return ''; }
    var d = new Date(iso);
    if (isNaN(d.getTime())) { return ''; }
    return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });
  }

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) { node.className = cls; }
    if (text !== undefined && text !== null) { node.textContent = text; }
    return node;
  }

  function csrfHeaders() {
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function init() {
    var root = document.getElementById('editor');
    if (!root) { return; }
    var state = JSON.parse(root.dataset.state);
    var titleInput = document.getElementById('post-title');
    var contentInput = document.getElementById('post-content');
    var statusLine = document.getElementById('save-status');
    var banner = document.getElementById('conflict-banner');
    var bannerText = document.getElementById('conflict-banner-text');
    var notice = document.getElementById('editor-notice');
    var dialog = document.getElementById('compare-dialog');
    var compareBody = document.getElementById('compare-body');
    var confirmBox = document.getElementById('overwrite-confirm');
    var store = window.blogDraftStore;
    var backupKey = 'draft-backup:' + state.memberId + ':' + state.postId;
    var server = null; // 충돌 때 서버 쪽 내용 {title, contentMd, version, savedAt}
    var diffTargets = [], diffIndex = -1;

    function content() { return { title: titleInput.value, contentMd: contentInput.value }; }

    function setContent(c) { titleInput.value = c.title || ''; contentInput.value = c.contentMd || ''; }

    function showNotice(text) { notice.textContent = text; notice.hidden = !text; }

    function setStatus(kind, detail) {
      statusLine.textContent = '';
      if (kind === 'saved') {
        statusLine.textContent = '✓ 저장됨 ' + hhmm((detail && detail.savedAt) || new Date().toISOString());
      } else if (kind === 'local') {
        statusLine.textContent = store.available === false
          ? '● 이 탭에만 저장됨 (이 브라우저는 기기 저장을 쓸 수 없어요, 동기화 대기)'
          : '● 이 기기에 저장됨 (동기화 대기)';
      } else if (kind === 'offline') {
        statusLine.textContent = '⚠ 오프라인 — 이 기기에 저장 중, 연결되면 자동 동기화';
      } else if (kind === 'conflict') {
        statusLine.appendChild(document.createTextNode('⚠ 다른 곳에서 수정됨 — 이 기기에만 저장 중 '));
        var button = el('button', null, '비교하기');
        button.type = 'button';
        button.addEventListener('click', openCompare);
        statusLine.appendChild(button);
      } else if (kind === 'error') {
        statusLine.textContent = '⚠ ' + ((detail && detail.message) || '저장하지 못했어요');
      }
    }

    function onConflict(serverContent) {
      server = serverContent;
      bannerText.textContent = '⚠ 다른 탭이나 기기에서 이 글이 수정되었어요(' + hhmm(server.savedAt)
        + '). 지금 내용은 이 기기에만 저장되고 있어요.';
      banner.hidden = false;
      setStatus('conflict');
    }

    function clearConflict() { server = null; banner.hidden = true; confirmBox.hidden = true; }

    var autosave = window.blogAutosave.create({
      memberId: state.memberId,
      postId: state.postId,
      baseVersion: state.version,
      initial: { title: state.title, contentMd: state.contentMd },
      getContent: content,
      onStatus: setStatus,
      onConflict: onConflict,
      localSaveDelayMs: state.localSaveDelayMs,
      serverSaveDelayMs: state.serverSaveDelayMs,
      serverSaveMaxIntervalMs: state.serverSaveMaxIntervalMs,
      retryMaxMs: state.retryMaxMs
    });

    // ----- 비교 창 -----

    function cell(side, row) {
      var type = row.type;
      var text = side === 'left' ? row.left : row.right;
      var cls = 'diff-cell';
      if (type === 'same') { cls += side === 'right' ? ' diff-same-right' : ''; }
      else if (text === null) { return el('div', cls + (side === 'right' ? ' diff-same-right' : '')); }
      else { cls += side === 'left' ? ' diff-del' : ' diff-add'; }
      var node = el('div', cls);
      if (type !== 'same') { node.appendChild(el('span', 'diff-sign', side === 'left' ? '− ' : '+ ')); }
      var parts = type === 'change' ? (side === 'left' ? row.leftParts : row.rightParts) : null;
      if (parts) {
        parts.forEach(function (p) { node.appendChild(p.changed ? el('mark', null, p.text) : document.createTextNode(p.text)); });
      } else {
        node.appendChild(document.createTextNode(text));
      }
      return node;
    }

    function rowNode(row) {
      var node = el('div', 'diff-row');
      node.appendChild(cell('left', row));
      node.appendChild(cell('right', row));
      if (row.type !== 'same') { node.dataset.change = 'true'; }
      return node;
    }

    function renderRows(container, rows) {
      window.blogDiff.fold(rows, FOLD_MIN, FOLD_KEEP).forEach(function (row) {
        if (row.type !== 'fold') { container.appendChild(rowNode(row)); return; }
        var button = el('button', 'diff-fold', '바뀌지 않은 ' + row.count + '줄 펼치기');
        button.type = 'button';
        button.addEventListener('click', function () {
          var frag = document.createDocumentFragment();
          row.rows.forEach(function (r) { frag.appendChild(rowNode(r)); });
          button.replaceWith(frag);
        });
        container.appendChild(button);
      });
    }

    function openCompare() {
      if (!server) { return; }
      var mine = content();
      compareBody.textContent = '';
      document.getElementById('compare-left-label').textContent =
        '저장된 내용 · ' + hhmm(server.savedAt) + ' (다른 탭·기기)';
      if ((server.title || '') !== mine.title) {
        var titleBox = el('div', 'diff-title');
        titleBox.appendChild(el('strong', null, '제목'));
        renderRows(titleBox, window.blogDiff.rows(server.title || '', mine.title));
        compareBody.appendChild(titleBox);
      }
      renderRows(compareBody, window.blogDiff.rows(server.contentMd || '', mine.contentMd));
      diffTargets = Array.prototype.slice.call(compareBody.querySelectorAll('[data-change]'));
      diffIndex = -1;
      document.getElementById('compare-count').textContent = '바뀐 줄 ' + diffTargets.length + '개';
      confirmBox.hidden = true;
      if (typeof dialog.showModal === 'function') { dialog.showModal(); } else { dialog.setAttribute('open', ''); }
      move(1);
    }

    function move(step) {
      if (diffTargets.length === 0) { return; }
      if (diffIndex >= 0) { diffTargets[diffIndex].classList.remove('diff-current'); }
      diffIndex = (diffIndex + step + diffTargets.length) % diffTargets.length;
      diffTargets[diffIndex].classList.add('diff-current');
      diffTargets[diffIndex].scrollIntoView({ block: 'center' });
    }

    function closeCompare() { if (dialog.open) { dialog.close(); } }

    document.getElementById('compare-prev').addEventListener('click', function () { move(-1); });
    document.getElementById('compare-next').addEventListener('click', function () { move(1); });
    document.getElementById('compare-close').addEventListener('click', closeCompare); // 배너·전송 멈춤 유지
    document.getElementById('conflict-compare').addEventListener('click', openCompare);

    document.getElementById('choose-mine').addEventListener('click', function () {
      document.getElementById('overwrite-confirm-text').textContent =
        hhmm(server.savedAt) + '에 저장된 내용이 지금 편집 중인 내용으로 바뀌어요. 정말 저장할까요?';
      confirmBox.hidden = false;
    });
    document.getElementById('overwrite-no').addEventListener('click', function () { confirmBox.hidden = true; });
    document.getElementById('overwrite-yes').addEventListener('click', function () {
      var version = server.version;
      clearConflict();
      closeCompare();
      autosave.overwriteWith(version);
    });

    document.getElementById('choose-server').addEventListener('click', function () {
      var mine = content();
      var now = Date.now();
      store.set(backupKey, { title: mine.title, contentMd: mine.contentMd, baseVersion: state.version,
        savedAt: new Date(now).toISOString(), expiresAt: now + state.backupTtlMs });
      var s = server;
      setContent(s);
      clearConflict();
      closeCompare();
      autosave.adoptServer(s);
      showNotice('편집 중인 내용은 이 기기에 7일 동안 백업돼요.');
    });

    document.getElementById('choose-new').addEventListener('click', function () {
      var mine = content();
      fetch('/api/posts', { method: 'POST', headers: csrfHeaders(), credentials: 'same-origin',
        body: JSON.stringify({ title: mine.title, contentMd: mine.contentMd }) })
        .then(function (res) { return res.json().then(function (data) { return { ok: res.ok, data: data }; }); })
        .then(function (r) {
          if (!r.ok) { showNotice(r.data.message || '새 임시글을 만들지 못했어요. 내용은 이 기기에 남아 있어요.'); return; }
          // 원래 글은 서버 내용 그대로. 이 탭의 못 보낸 내용은 새 글로 옮겼으니 지운다
          autosave.disable().then(function () { window.location.assign('/write/' + r.data.postId); });
        }, function () { showNotice('연결되지 않아 새 임시글을 만들지 못했어요. 내용은 이 기기에 남아 있어요.'); });
    });

    // ----- 입력·버튼 -----

    titleInput.addEventListener('input', autosave.onInput);
    contentInput.addEventListener('input', autosave.onInput);
    document.getElementById('manual-save').addEventListener('click', function () {
      if (autosave.isConflict()) { openCompare(); return; }
      autosave.saveNow();
    });

    var discard = document.getElementById('discard-working-copy');
    if (discard) {
      discard.addEventListener('click', function () {
        if (!window.confirm('고치던 내용을 버리고 마지막 발행본으로 되돌릴까요?')) { return; }
        fetch('/api/posts/' + state.postId + '/working-copy', { method: 'DELETE', headers: csrfHeaders(), credentials: 'same-origin' })
          .then(function (res) {
            if (res.status !== 204) { showNotice('변경을 취소하지 못했어요.'); return; }
            autosave.disable().then(function () { window.location.reload(); });
          }, function () { showNotice('연결되지 않아 변경을 취소하지 못했어요.'); });
      });
    }

    // 005 발행 화면이 쓰는 연결점
    window.blogEditor = {
      state: state,
      content: content,
      baseVersion: function () { return autosave.baseVersion(); },
      conflict: function (serverContent) { autosave.enterConflict(); onConflict(serverContent); openCompare(); },
      isConflict: function () { return autosave.isConflict(); },
      openCompare: openCompare,
      published: function () { return autosave.disable(); }
    };

    // ----- 열 때 로컬 데이터 판정(FR-019) -----

    store.get(backupKey).then(function (backup) {
      if (backup && backup.expiresAt && backup.expiresAt < Date.now()) { store.remove(backupKey); }
    });
    store.get(autosave.key).then(function (local) {
      var serverNow = { title: state.title, contentMd: state.contentMd, version: state.version, savedAt: state.savedAt };
      if (!local || !local.dirty || (local.title === state.title && local.contentMd === state.contentMd)) {
        setStatus('saved', { savedAt: state.savedAt });
        return;
      }
      setContent(local);
      if (local.baseVersion === state.version) {
        showNotice('이 기기에 저장되지 않은 변경을 불러왔어요');
        autosave.resumeLocal();
      } else {
        autosave.enterConflict();
        onConflict(serverNow);
        openCompare();
      }
    });
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

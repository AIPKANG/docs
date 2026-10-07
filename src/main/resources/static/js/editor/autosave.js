/*
 * 3단계 저장의 브라우저 쪽(004 FR-004·FR-005·FR-009~FR-012, contracts/web-routes.md §3).
 * - 입력이 localSaveDelay(1초) 멈추면 IndexedDB에 dirty=true로 저장.
 * - dirty이고 입력이 serverSaveDelay(3초) 멈추거나 마지막 전송 뒤 serverSaveMaxInterval(30초), 탭이 가려질 때, 떠날 때 서버로.
 * - 탭 하나에서는 요청을 한 번에 하나만 보낸다. 실패하면 2초부터 두 배씩 retryMax(60초)까지 + 0~1초 무작위 지연.
 * - 409면 서버 전송만 멈추고 로컬 저장은 계속한다(편집을 막지 않음). 해제는 비교 창 선택으로만.
 * 화면 묶기는 editor.js가 한다. 상태는 onStatus(kind, detail)로 알린다: saved | local | offline | conflict | error.
 */
(function () {
  'use strict';

  var KEEPALIVE_MAX_BYTES = 60000; // fetch keepalive 본문 한도(64KB) 안쪽

  function csrfHeaders() {
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function create(opts) {
    var store = window.blogDraftStore;
    var key = 'draft:' + opts.memberId + ':' + opts.postId;
    var base = opts.baseVersion;
    var acked = opts.initial;          // 서버가 받아들인 마지막 내용 {title, contentMd}
    var dirty = false;
    var conflict = false;
    var disabled = false;
    var inFlight = false;
    var pendingSend = null;            // {manual}
    var retryDelay = 0;
    var retryTimer = null, localTimer = null, idleTimer = null, maxTimer = null;

    function same(a, b) { return a && b && a.title === b.title && a.contentMd === b.contentMd; }

    function snapshot() { return opts.getContent(); }

    function record(isDirty) {
      var c = snapshot();
      return { title: c.title, contentMd: c.contentMd, baseVersion: base, dirty: isDirty, pendingImages: [], updatedAt: Date.now() };
    }

    function saveLocal() {
      clearTimeout(localTimer);
      dirty = !same(snapshot(), acked);
      if (!dirty) { return store.set(key, record(false)); }
      if (!conflict) { opts.onStatus(navigator.onLine === false ? 'offline' : 'local'); }
      return store.set(key, record(true));
    }

    function clearServerTimers() {
      clearTimeout(idleTimer); idleTimer = null;
      clearTimeout(maxTimer); maxTimer = null;
    }

    function scheduleRetry(ms) {
      clearTimeout(retryTimer);
      retryTimer = setTimeout(function () { retryTimer = null; send({}); }, ms);
    }

    function backoff() {
      retryDelay = retryDelay ? Math.min(retryDelay * 2, opts.retryMaxMs) : 2000;
      scheduleRetry(retryDelay + Math.floor(Math.random() * 1000));
    }

    function send(options) {
      options = options || {};
      if (disabled || conflict) { return Promise.resolve(); }
      if (inFlight) { pendingSend = { manual: !!options.manual || !!(pendingSend && pendingSend.manual) }; return Promise.resolve(); }
      dirty = !same(snapshot(), acked);
      if (!dirty && !options.manual) { return Promise.resolve(); }
      if (retryTimer && !options.manual && !options.force) { return Promise.resolve(); }
      clearTimeout(retryTimer); retryTimer = null;
      clearServerTimers();
      var sent = snapshot();
      var body = JSON.stringify({ title: sent.title, contentMd: sent.contentMd, baseVersion: base });
      var url = '/api/posts/' + opts.postId + (options.manual ? '/draft' : '/autosave');
      var init = { method: 'PUT', headers: csrfHeaders(), credentials: 'same-origin', body: body };
      if (options.keepalive && body.length <= KEEPALIVE_MAX_BYTES) { init.keepalive = true; }
      inFlight = true;
      return store.set(key, record(true)).then(function () {
        return fetch(url, init);
      }).then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (data) { return { res: res, data: data }; });
      }).then(function (r) {
        handle(r.res, r.data, sent, options);
      }, function () {
        // 네트워크 오류: 내용은 IndexedDB에 있다
        opts.onStatus(navigator.onLine === false ? 'offline' : 'local');
        backoff();
      }).then(function () {
        inFlight = false;
        if (pendingSend) { var next = pendingSend; pendingSend = null; send(next); }
      });
    }

    function accept(version, sent, savedAt) {
      base = version;
      acked = sent;
      retryDelay = 0;
      dirty = !same(snapshot(), acked);
      store.set(key, record(dirty));
      if (dirty) { idleTimer = setTimeout(function () { send({}); }, opts.serverSaveDelayMs); }
      opts.onStatus(dirty ? 'local' : 'saved', { savedAt: savedAt });
    }

    function handle(res, data, sent, options) {
      if (res.ok) { accept(data.version, sent, data.savedAt); return; }
      if (res.status === 409 && data.server) {
        conflict = true;
        clearServerTimers();
        saveLocal();
        opts.onConflict(data.server);
        return;
      }
      if (res.status === 503 && data.code === 'SAVE_DELAYED' && typeof data.version === 'number') {
        base = data.version; acked = sent; retryDelay = 0;
        dirty = !same(snapshot(), acked);
        store.set(key, record(dirty));
        opts.onStatus('error', { message: data.message });
        return;
      }
      if (res.status === 429) {
        var after = parseInt(res.headers.get('Retry-After'), 10);
        opts.onStatus('local');
        scheduleRetry(((isNaN(after) ? 5 : after) * 1000) + Math.floor(Math.random() * 500));
        return;
      }
      if (res.status >= 500 || res.status === 0) { opts.onStatus('local'); backoff(); return; }
      // 400·401·403·404·413: 다시 보내도 같은 결과 — 내용은 이 기기에 남기고 알린다
      opts.onStatus('error', { message: data.message || '저장하지 못했어요. 내용은 이 기기에 남아 있어요.', status: res.status });
    }

    function onInput() {
      if (disabled) { return; }
      clearTimeout(localTimer);
      localTimer = setTimeout(saveLocal, opts.localSaveDelayMs);
      if (conflict) { return; }
      clearTimeout(idleTimer);
      idleTimer = setTimeout(function () { saveLocal(); send({}); }, opts.serverSaveDelayMs);
      if (!maxTimer) { maxTimer = setTimeout(function () { maxTimer = null; saveLocal(); send({}); }, opts.serverSaveMaxIntervalMs); }
    }

    function onHide() {
      if (disabled) { return; }
      saveLocal();
      send({ keepalive: true, force: true });
    }

    window.addEventListener('online', function () { if (dirty) { send({ force: true }); } });
    window.addEventListener('offline', function () { if (dirty && !conflict) { opts.onStatus('offline'); } });
    document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'hidden') { onHide(); } });
    window.addEventListener('pagehide', function () {
      if (disabled) { return; }
      dirty = !same(snapshot(), acked);
      if (dirty) { onHide(); } else { store.remove(key); } // 동기화된 상태로 떠나면 이 기기 데이터 삭제(FR-012)
    });
    window.addEventListener('beforeunload', function (event) {
      if (disabled) { return; }
      dirty = !same(snapshot(), acked);
      if (dirty || inFlight) { event.preventDefault(); event.returnValue = ''; }
    });

    return {
      key: key,
      onInput: onInput,
      /* 수동 저장: 바뀐 게 없어도 보낸다 */
      saveNow: function () { saveLocal(); return send({ manual: true, force: true }); },
      isConflict: function () { return conflict; },
      baseVersion: function () { return base; },
      isDirty: function () { return !same(snapshot(), acked); },
      /* 불러온 로컬 내용을 이어 쓸 때: 출발 버전 그대로, 곧 서버로 */
      resumeLocal: function () { saveLocal(); idleTimer = setTimeout(function () { send({}); }, opts.serverSaveDelayMs); },
      /* 충돌 상태로 시작(열 때 버전이 다름) */
      enterConflict: function () { conflict = true; clearServerTimers(); },
      /* [편집 중인 내용으로 저장]: 서버 버전을 출발점으로 수동 저장 */
      overwriteWith: function (serverVersion) { base = serverVersion; conflict = false; return this.saveNow(); },
      /* [저장된 내용 불러오기]: 에디터가 이미 서버 내용으로 바뀐 뒤 부른다 */
      adoptServer: function (server) {
        base = server.version; acked = { title: server.title, contentMd: server.contentMd }; conflict = false; dirty = false;
        store.set(key, record(false));
        opts.onStatus('saved', { savedAt: server.savedAt });
      },
      /* 이 글의 로컬 데이터를 지우고 더 저장하지 않음(변경 취소·새 임시글로 이동) */
      disable: function () { disabled = true; clearServerTimers(); clearTimeout(localTimer); clearTimeout(retryTimer); return store.remove(key); }
    };
  }

  window.blogAutosave = { create: create };
})();

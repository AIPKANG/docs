/*
 * 로그아웃 시 브라우저 임시 작성 데이터 삭제(FR-031, SC-008, research R-11).
 * - 로그아웃 버튼(form[data-logout-form][data-member-id]) 제출 전에 IndexedDB의 draft:{memberId}:*, draft-backup:{memberId}:* 키를 지운다.
 * - 로그아웃 뒤 홈 화면의 1회용 값(#logout-cleanup[data-member-id])으로 같은 정리를 한 번 더 실행한다(첫 시도 실패 대비).
 * 그 회원의 키만 지운다 — 같은 브라우저의 다른 계정 데이터는 건드리지 않는다.
 * 데이터베이스 이름은 indexedDB.databases()로 찾고, 지원하지 않는 브라우저는 localforage 기본 이름을 쓴다.
 */
(function () {
  'use strict';

  var FALLBACK_DATABASES = ['localforage'];
  var TIMEOUT_MS = 1500;

  function prefixesFor(memberId) {
    return ['draft:' + memberId + ':', 'draft-backup:' + memberId + ':'];
  }

  function matches(key, prefixes) {
    if (typeof key !== 'string') { return false; }
    for (var i = 0; i < prefixes.length; i++) {
      if (key.indexOf(prefixes[i]) === 0) { return true; }
    }
    return false;
  }

  function databaseNames() {
    if (!window.indexedDB) { return Promise.resolve([]); }
    if (typeof indexedDB.databases === 'function') {
      return indexedDB.databases().then(function (list) {
        return list.map(function (d) { return d.name; }).filter(Boolean);
      }).catch(function () { return FALLBACK_DATABASES; });
    }
    return Promise.resolve(FALLBACK_DATABASES);
  }

  function cleanDatabase(name, prefixes) {
    return new Promise(function (resolve) {
      var request;
      try { request = indexedDB.open(name); } catch (e) { resolve(); return; }
      request.onupgradeneeded = function (event) {
        // 없는 데이터베이스를 새로 만들지 않는다
        event.target.transaction.abort();
      };
      request.onerror = function () { resolve(); };
      request.onblocked = function () { resolve(); };
      request.onsuccess = function () {
        var db = request.result;
        var stores = Array.prototype.slice.call(db.objectStoreNames);
        if (stores.length === 0) { db.close(); resolve(); return; }
        var tx = db.transaction(stores, 'readwrite');
        stores.forEach(function (storeName) {
          var store = tx.objectStore(storeName);
          var cursorRequest = store.openKeyCursor ? store.openKeyCursor() : store.openCursor();
          cursorRequest.onsuccess = function () {
            var cursor = cursorRequest.result;
            if (!cursor) { return; }
            if (matches(cursor.primaryKey, prefixes)) { store.delete(cursor.primaryKey); }
            cursor.continue();
          };
        });
        tx.oncomplete = function () { db.close(); resolve(); };
        tx.onerror = function () { db.close(); resolve(); };
        tx.onabort = function () { db.close(); resolve(); };
      };
    });
  }

  function cleanup(memberId) {
    if (!memberId) { return Promise.resolve(); }
    var prefixes = prefixesFor(memberId);
    return databaseNames().then(function (names) {
      return Promise.all(names.map(function (name) { return cleanDatabase(name, prefixes); }));
    }).catch(function () { /* 정리 실패는 로그아웃을 막지 않는다 */ });
  }

  function withTimeout(promise) {
    return Promise.race([promise, new Promise(function (resolve) { setTimeout(resolve, TIMEOUT_MS); })]);
  }

  function bindForms() {
    var forms = document.querySelectorAll('form[data-logout-form]');
    for (var i = 0; i < forms.length; i++) {
      forms[i].addEventListener('submit', function (event) {
        var form = event.currentTarget;
        if (form.dataset.cleaned === 'true') { return; }
        event.preventDefault();
        withTimeout(cleanup(form.dataset.memberId)).then(function () {
          form.dataset.cleaned = 'true';
          form.submit();
        });
      });
    }
  }

  function cleanupAfterLogout() {
    var marker = document.getElementById('logout-cleanup');
    if (marker) { cleanup(marker.dataset.memberId); }
  }

  window.blogAuthLogout = { cleanup: cleanup };

  function init() {
    bindForms();
    cleanupAfterLogout();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

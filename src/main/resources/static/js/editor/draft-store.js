/*
 * 브라우저 임시 저장소(004 FR-004, data-model §3). IndexedDB 데이터베이스 blog-drafts, 저장소 kv.
 * 키: draft:{memberId}:{postId}, draft-backup:{memberId}:{postId}. 인증 정보는 넣지 않는다.
 * 로그아웃 때 auth-logout.js가 모든 IndexedDB에서 그 회원 접두어 키를 지운다(001).
 * IndexedDB를 쓸 수 없으면(사생활 보호 모드 등) 이 탭 메모리에만 두고 available=false로 알린다.
 */
(function () {
  'use strict';

  var DB_NAME = 'blog-drafts';
  var STORE = 'kv';
  var memory = new Map();
  var dbPromise = null;

  function open() {
    if (dbPromise) { return dbPromise; }
    dbPromise = new Promise(function (resolve, reject) {
      if (!window.indexedDB) { reject(new Error('no indexedDB')); return; }
      var request;
      try { request = indexedDB.open(DB_NAME, 1); } catch (e) { reject(e); return; }
      request.onupgradeneeded = function () {
        if (!request.result.objectStoreNames.contains(STORE)) { request.result.createObjectStore(STORE); }
      };
      request.onsuccess = function () { resolve(request.result); };
      request.onerror = function () { reject(request.error); };
      request.onblocked = function () { reject(new Error('blocked')); };
    });
    dbPromise.then(function () { api.available = true; }, function () { api.available = false; });
    return dbPromise;
  }

  function run(mode, fn) {
    return open().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(STORE, mode);
        var result;
        fn(tx.objectStore(STORE), function (value) { result = value; });
        tx.oncomplete = function () { resolve(result); };
        tx.onerror = function () { reject(tx.error); };
        tx.onabort = function () { reject(tx.error); };
      });
    });
  }

  var api = {
    available: true,
    get: function (key) {
      return run('readonly', function (store, done) {
        var req = store.get(key);
        req.onsuccess = function () { done(req.result); };
      }).catch(function () { return memory.get(key); });
    },
    set: function (key, value) {
      memory.set(key, value);
      return run('readwrite', function (store) { store.put(value, key); }).catch(function () { /* 메모리에 남음 */ });
    },
    remove: function (key) {
      memory.delete(key);
      return run('readwrite', function (store) { store.delete(key); }).catch(function () { /* 무시 */ });
    }
  };

  window.blogDraftStore = api;
})();

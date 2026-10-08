/*
 * 에디터 사진 넣기(008 FR-001·FR-002·FR-017·FR-028·FR-033, 04 §4).
 * - [사진] 버튼·붙여넣기·끌어놓기 → 브라우저에서 원본(긴 변 1920px, WebP 0.8, 다시 그려 EXIF 제거; GIF는 그대로)과
 *   썸네일(가로 640px, WebP 0.8, GIF는 첫 장면)을 만들고 presign → 저장소에 두 파일 직접 PUT → complete.
 * - 넣는 순간 본문에 ![업로드 중](local:id)을 넣고, 성공하면 ![](진짜 주소)로 바꾼다. 대체글은 비워 두고 파일 이름은 넣지 않는다.
 * - 실패·오프라인이면 사진을 IndexedDB(draft:{memberId}:{postId}:img:{id})에 두고 ![업로드 대기](local:id)로 남겨,
 *   연결되거나 다시 열 때 자동으로 재시도한다. 자동 저장은 그대로 되고 발행만 막힌다(005 PENDING_IMAGES).
 */
(function () {
  'use strict';

  var MAX_SIDE = 1920, THUMB_WIDTH = 640, QUALITY = 0.8;
  var TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];

  function csrfHeaders() {
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function toBlob(canvas, type, quality) {
    return new Promise(function (resolve) { canvas.toBlob(resolve, type, quality); });
  }

  function draw(bitmap, width) {
    var scale = width / bitmap.width;
    var canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return canvas;
  }

  /* 원본·썸네일 만들기. 썸네일을 WebP로 만들 수 없는 브라우저는 썸네일 없이(카드·GIF는 원본을 씀) */
  function prepare(file) {
    return createImageBitmap(file).then(function (bitmap) {
      var original;
      if (file.type === 'image/gif') {
        original = Promise.resolve({ blob: file, type: 'image/gif' });
      } else {
        var longSide = Math.max(bitmap.width, bitmap.height);
        var width = longSide > MAX_SIDE ? Math.round(bitmap.width * MAX_SIDE / longSide) : bitmap.width;
        original = toBlob(draw(bitmap, width), 'image/webp', QUALITY).then(function (blob) {
          if (blob && blob.type === 'image/webp') { return { blob: blob, type: 'image/webp' }; }
          return toBlob(draw(bitmap, width), 'image/jpeg', 0.85).then(function (jpeg) { return { blob: jpeg, type: 'image/jpeg' }; });
        });
      }
      var thumbWidth = Math.min(THUMB_WIDTH, bitmap.width);
      var thumb = toBlob(draw(bitmap, thumbWidth), 'image/webp', QUALITY).then(function (blob) {
        return blob && blob.type === 'image/webp' ? blob : null;
      });
      return Promise.all([original, thumb]);
    });
  }

  /* 움직이는 WebP(ANIM)·APNG(acTL)는 첫 장면만 남는다는 안내(FR-033) */
  function isAnimatedStill(file) {
    if (file.type !== 'image/webp' && file.type !== 'image/png') { return Promise.resolve(false); }
    return file.slice(0, 65536).arrayBuffer().then(function (buf) {
      var text = String.fromCharCode.apply(null, new Uint8Array(buf.slice(0, Math.min(buf.byteLength, 65536))));
      return text.indexOf('ANIM') >= 0 || text.indexOf('acTL') >= 0;
    }).catch(function () { return false; });
  }

  function init() {
    var editor = window.blogEditor;
    var textarea = document.getElementById('post-content');
    var button = document.getElementById('image-pick');
    var input = document.getElementById('image-file');
    var notice = document.getElementById('image-notice');
    if (!editor || !textarea || !button || !input) { return; }
    var store = window.blogDraftStore;
    var prefix = 'draft:' + editor.state.memberId + ':' + editor.state.postId + ':img:';
    var indexKey = prefix + 'index';

    function say(text) { notice.textContent = text || ''; notice.hidden = !text; }

    function replaceMarker(id, markdown) {
      var re = new RegExp('!\\[[^\\]]*\\]\\(local:' + id + '\\)', 'g');
      textarea.value = textarea.value.replace(re, markdown);
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function insertAtCursor(text) {
      var start = textarea.selectionStart, end = textarea.selectionEnd;
      var before = textarea.value.slice(0, start), after = textarea.value.slice(end);
      var pad = before && !/\n$/.test(before) ? '\n\n' : '';
      textarea.value = before + pad + text + '\n\n' + after;
      textarea.selectionStart = textarea.selectionEnd = (before + pad + text).length + 2;
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function readIndex() { return store.get(indexKey).then(function (v) { return Array.isArray(v) ? v : []; }); }
    function writeIndex(ids) { return store.set(indexKey, ids); }

    function remember(id, file) {
      return store.set(prefix + id, { blob: file, type: file.type }).then(readIndex).then(function (ids) {
        if (ids.indexOf(id) < 0) { ids.push(id); }
        return writeIndex(ids);
      });
    }

    function forget(id) {
      return store.remove(prefix + id).then(readIndex).then(function (ids) {
        return writeIndex(ids.filter(function (x) { return x !== id; }));
      });
    }

    function put(url, headers, blob) {
      return fetch(url, { method: 'PUT', headers: headers, body: blob, credentials: 'omit' }).then(function (res) {
        if (!res.ok) { throw new Error('put ' + res.status); }
      });
    }

    function failMessage(res, data) {
      if (res.status === 409) { return data.message || '저장 공간(1GB)을 다 썼어요.'; }
      if (res.status === 429) { return data.message || '잠시 뒤 다시 올려 주세요.'; }
      if (res.status === 400) { return data.message || '이 사진은 올릴 수 없어요.'; }
      return null; // 다시 시도할 수 있는 실패
    }

    function upload(id, file) {
      return prepare(file).then(function (parts) {
        var original = parts[0], thumb = parts[1];
        return fetch('/api/images/presign', { method: 'POST', headers: csrfHeaders(), credentials: 'same-origin',
          body: JSON.stringify({ purpose: 'POST', contentType: original.type, size: original.blob.size,
            thumbSize: thumb ? thumb.size : null }) })
          .then(function (res) { return res.json().then(function (d) { return { res: res, data: d }; }); })
          .then(function (r) {
            if (!r.res.ok) { var e = new Error('presign'); e.final = failMessage(r.res, r.data); throw e; }
            var p = r.data;
            var puts = [put(p.uploadUrl, p.headers, original.blob)];
            if (thumb && p.thumbUploadUrl) { puts.push(put(p.thumbUploadUrl, p.thumbHeaders, thumb)); }
            return Promise.all(puts).then(function () {
              return fetch('/api/images/' + p.imageId + '/complete', { method: 'POST', headers: csrfHeaders(),
                credentials: 'same-origin' });
            });
          })
          .then(function (res) { return res.json().then(function (d) { return { res: res, data: d }; }); })
          .then(function (r) {
            if (!r.res.ok) { var e = new Error('complete'); e.final = failMessage(r.res, r.data); throw e; }
            replaceMarker(id, '![](' + r.data.url + ')');
            return forget(id).then(checkUsage);
          });
      }).catch(function (e) {
        if (e && e.final) {
          replaceMarker(id, '');
          say(e.final);
          return forget(id);
        }
        replaceMarker(id, '![업로드 대기](local:' + id + ')');
        say('사진을 올리지 못했어요. 연결되면 자동으로 다시 올려요.');
        return null;
      });
    }

    function checkUsage() {
      return fetch('/api/me/storage', { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
        .then(function (res) { return res.ok ? res.json() : null; })
        .then(function (u) {
          if (u && u.quotaBytes && u.usedBytes * 10 >= u.quotaBytes * 9) { say('저장 공간의 90%를 썼어요.'); }
        }).catch(function () {});
    }

    function add(file) {
      if (TYPES.indexOf(file.type) < 0) { say('jpg·png·gif·webp 사진만 올릴 수 있어요.'); return; }
      if (file.size > 10 * 1024 * 1024 && file.type === 'image/gif') { say('GIF는 10MB까지 올릴 수 있어요.'); return; }
      var id = Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
      insertAtCursor('![업로드 중](local:' + id + ')');
      isAnimatedStill(file).then(function (animated) {
        if (animated) { say('움직이는 WebP·APNG는 첫 장면만 남아요. 움직임을 살리려면 GIF로 올려 주세요.'); }
      });
      remember(id, file).then(function () { return upload(id, file); });
    }

    function retryPending() {
      readIndex().then(function (ids) {
        ids.forEach(function (id) {
          if (textarea.value.indexOf('local:' + id) < 0) { forget(id); return; }
          store.get(prefix + id).then(function (item) { if (item && item.blob) { upload(id, item.blob); } });
        });
      });
    }

    button.addEventListener('click', function () { input.click(); });
    input.addEventListener('change', function () {
      Array.prototype.forEach.call(input.files, add);
      input.value = '';
    });
    textarea.addEventListener('paste', function (e) {
      var files = e.clipboardData && e.clipboardData.files;
      if (files && files.length) { e.preventDefault(); Array.prototype.forEach.call(files, add); }
    });
    textarea.addEventListener('dragover', function (e) { e.preventDefault(); });
    textarea.addEventListener('drop', function (e) {
      var files = e.dataTransfer && e.dataTransfer.files;
      if (files && files.length) { e.preventDefault(); Array.prototype.forEach.call(files, add); }
    });
    window.addEventListener('online', retryPending);
    setTimeout(retryPending, 1500); // 다시 열었을 때 남은 대기 사진
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

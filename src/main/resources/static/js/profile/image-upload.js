/*
 * 프로필 이미지 업로드 흐름(003 research R-7): presign → 저장소로 직접 PUT → complete.
 * 사진 데이터는 앱 서버를 거치지 않는다. 결과 { imageId, url }. 실패하면 Error(message)를 던진다.
 * 전역 BlogProfileUpload.upload(blob, originalName)
 */
(function () {
  'use strict';

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function messageOf(response, fallback) {
    return response.json().then(function (body) {
      return (body && body.message) || fallback;
    }, function () { return fallback; });
  }

  function upload(blob, originalName) {
    var type = blob.type || 'image/webp';
    return fetch('/api/images/presign', {
      method: 'POST', headers: csrfHeaders(), credentials: 'same-origin',
      body: JSON.stringify({ purpose: 'PROFILE', contentType: type, size: blob.size, originalName: originalName || 'profile.webp' })
    }).then(function (res) {
      if (!res.ok) { return messageOf(res, '사진을 올릴 수 없어요').then(function (m) { throw new Error(m); }); }
      return res.json();
    }).then(function (target) {
      return fetch(target.uploadUrl, { method: target.method || 'PUT', headers: target.headers, body: blob, credentials: 'omit' })
        .then(function (res) {
          if (!res.ok) { throw new Error('사진을 저장소에 올리지 못했어요'); }
          return fetch('/api/images/' + encodeURIComponent(target.imageId) + '/complete', {
            method: 'POST', headers: csrfHeaders(), credentials: 'same-origin'
          });
        });
    }).then(function (res) {
      if (!res.ok) { return messageOf(res, '사진을 올릴 수 없어요').then(function (m) { throw new Error(m); }); }
      return res.json();
    });
  }

  /** 캔버스를 WebP(품질 0.85)로, 브라우저가 WebP를 못 만들면 PNG로. */
  function canvasToBlob(canvas) {
    return new Promise(function (resolve, reject) {
      canvas.toBlob(function (blob) {
        if (blob && blob.type === 'image/webp') { resolve(blob); return; }
        canvas.toBlob(function (png) { png ? resolve(png) : reject(new Error('이미지를 만들지 못했어요')); }, 'image/png');
      }, 'image/webp', 0.85);
    });
  }

  window.BlogProfileUpload = { upload: upload, canvasToBlob: canvasToBlob, csrfHeaders: csrfHeaders };
})();

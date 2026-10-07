/*
 * 소셜 프로필 사진 복사(11 §4-2, FR-021·FR-022). 서버는 소셜 사진 주소로 요청하지 않는다(SSRF 회피):
 * 브라우저가 사진을 받아(CORS, 5초 제한) 가운데 정사각형을 256×256 WebP로 만들고 일반 업로드 흐름으로 우리 저장소에 올린 뒤
 * PATCH /api/me/profile {profileImageId}로 연결한다. 실패하면 기본 이미지로 두고 안내만 보인다(가입은 이미 끝남).
 */
(function () {
  'use strict';

  var TIMEOUT_MS = 5000;

  function fail() {
    document.getElementById('social-picture-progress').hidden = true;
    document.getElementById('social-picture-failed').hidden = false;
  }

  function fetchPicture(url) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, TIMEOUT_MS);
    return fetch(url, { mode: 'cors', credentials: 'omit', referrerPolicy: 'no-referrer', signal: controller.signal })
      .then(function (res) {
        if (!res.ok) { throw new Error('download failed'); }
        return res.blob();
      })
      .then(function (blob) { return createImageBitmap(blob); })
      .finally(function () { clearTimeout(timer); });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var holder = document.getElementById('social-picture');
    var url = holder && holder.dataset.pictureUrl;
    if (!url) { fail(); return; }
    fetchPicture(url).then(function (bitmap) {
      var canvas = window.BlogCropper.centerSquare(bitmap, bitmap.width, bitmap.height);
      return window.BlogProfileUpload.canvasToBlob(canvas);
    }).then(function (blob) {
      return window.BlogProfileUpload.upload(blob, 'social-profile.webp');
    }).then(function (result) {
      return fetch('/api/me/profile', {
        method: 'PATCH', headers: window.BlogProfileUpload.csrfHeaders(), credentials: 'same-origin',
        body: JSON.stringify({ profileImageId: result.imageId })
      });
    }).then(function (res) {
      if (!res.ok) { throw new Error('attach failed'); }
      window.location.assign('/');
    }).catch(fail);
  });
})();

/*
 * 설정 화면(11 §2). 업무 규칙은 서버가 판정하고, 이 스크립트는 바뀐 칸만 보내고 칸별 오류를 그린다.
 * - 프로필: PATCH /api/me/profile (닉네임·소개·profileImageId)
 * - 비밀번호: POST /api/me/password, 기본 공개 범위: PATCH /api/me/settings
 */
(function () {
  'use strict';

  var csrf = function () { return window.BlogProfileUpload.csrfHeaders(); };

  function codePoints(text) { return Array.from(text || '').length; }

  function clearErrors(form) {
    form.querySelectorAll('[data-error-for]').forEach(function (el) { el.textContent = ''; });
  }

  function showErrors(form, body, fallbackField) {
    if (body && Array.isArray(body.errors)) {
      body.errors.forEach(function (err) {
        var el = form.querySelector('[data-error-for="' + err.field + '"]');
        if (el) { el.textContent = err.message; }
      });
      return;
    }
    var el = form.querySelector('[data-error-for="' + fallbackField + '"]');
    if (el) { el.textContent = (body && body.message) || '저장하지 못했어요'; }
  }

  function send(method, url, payload) {
    return fetch(url, { method: method, headers: csrf(), credentials: 'same-origin', body: JSON.stringify(payload) })
      .then(function (res) {
        if (res.status === 204) { return { ok: true, body: null }; }
        return res.json().then(function (body) { return { ok: res.ok, body: body }; },
          function () { return { ok: res.ok, body: null }; });
      });
  }

  function renderAvatar(url) {
    var holder = document.getElementById('profile-avatar');
    if (!holder) { return; }
    if (url) {
      holder.innerHTML = '';
      var img = document.createElement('img');
      img.className = 'avatar'; img.alt = ''; img.width = 96; img.height = 96; img.src = url;
      img.style.width = '96px'; img.style.height = '96px';
      holder.appendChild(img);
    } else if (holder.dataset.defaultHtml) {
      holder.innerHTML = holder.dataset.defaultHtml;
    }
  }

  function initProfile() {
    var form = document.getElementById('profile-form');
    if (!form) { return; }
    var nickname = document.getElementById('nickname');
    var bio = document.getElementById('bio');
    var count = document.getElementById('bio-count');
    var status = document.getElementById('profile-status');
    var holder = document.getElementById('profile-avatar');
    var originalNickname = form.dataset.nickname || '';
    var originalBio = bio.value;
    var image = { changed: false, id: null };

    var defaultSpan = holder.querySelector('.avatar-default');
    if (defaultSpan) { holder.dataset.defaultHtml = holder.innerHTML; }

    var updateCount = function () { count.textContent = String(codePoints(bio.value.trim())); };
    bio.addEventListener('input', updateCount);
    updateCount();

    var pick = document.getElementById('profile-image-pick');
    var file = document.getElementById('profile-image-file');
    if (pick && file) {
      pick.addEventListener('click', function () { file.click(); });
      file.addEventListener('change', function () {
        var chosen = file.files && file.files[0];
        file.value = '';
        if (!chosen) { return; }
        clearErrors(form);
        var cropStatus = document.getElementById('cropper-status');
        window.BlogCropper.open(chosen).then(function (canvas) {
          status.textContent = '사진을 올리는 중이에요…';
          return window.BlogProfileUpload.canvasToBlob(canvas).then(function (blob) {
            return window.BlogProfileUpload.upload(blob, chosen.name);
          });
        }).then(function (result) {
          image = { changed: true, id: result.imageId };
          renderAvatar(result.url);
          status.textContent = '[저장]을 누르면 프로필에 적용돼요';
        }).catch(function (err) {
          if (err && err.message === 'cancelled') { return; }
          status.textContent = '';
          var el = form.querySelector('[data-error-for="profileImageId"]');
          if (el) { el.textContent = err.message; }
          if (cropStatus) { cropStatus.textContent = ''; }
        });
      });
    }

    var reset = document.getElementById('profile-image-reset');
    if (reset) {
      reset.addEventListener('click', function () {
        image = { changed: true, id: null };
        if (!holder.dataset.defaultHtml) {
          holder.innerHTML = '<span class="avatar avatar-default avatar-c' + colorIndex(holder.dataset.handle) +
            '" aria-hidden="true" style="width:96px;height:96px;font-size:48px"></span>';
          holder.querySelector('span').textContent = initial(nickname.value || originalNickname);
          holder.dataset.defaultHtml = holder.innerHTML;
        }
        renderAvatar(null);
        status.textContent = '[저장]을 누르면 기본 이미지로 바뀌어요';
      });
    }

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      clearErrors(form);
      var payload = {};
      if (!nickname.disabled && nickname.value.trim() !== originalNickname) { payload.nickname = nickname.value; }
      if (bio.value !== originalBio) { payload.bio = bio.value; }
      if (image.changed) { payload.profileImageId = image.id; }
      if (Object.keys(payload).length === 0) { status.textContent = '바뀐 내용이 없어요'; return; }
      status.textContent = '저장하는 중이에요…';
      send('PATCH', '/api/me/profile', payload).then(function (result) {
        if (result.ok) {
          originalNickname = result.body.nickname;
          originalBio = result.body.bio || '';
          bio.value = originalBio;
          nickname.value = originalNickname;
          image.changed = false;
          updateCount();
          status.textContent = '저장했어요';
        } else {
          status.textContent = '';
          showErrors(form, result.body, 'nickname');
        }
      }).catch(function () { status.textContent = '저장하지 못했어요. 잠시 후 다시 시도해 주세요'; });
    });
  }

  // 서버 ProfileAvatar와 같은 규칙(Java String.hashCode → floorMod 8, 첫 글자 대문자)
  function colorIndex(handle) {
    var h = 0;
    for (var i = 0; i < (handle || '').length; i++) { h = (Math.imul(31, h) + handle.charCodeAt(i)) | 0; }
    return ((h % 8) + 8) % 8;
  }
  function initial(name) {
    var first = Array.from(name || '?')[0] || '?';
    return first.toUpperCase();
  }

  function initPassword() {
    var form = document.getElementById('password-form');
    if (!form) { return; }
    var status = document.getElementById('password-status');
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      clearErrors(form);
      status.textContent = '';
      send('POST', '/api/me/password', {
        currentPassword: form.currentPassword.value,
        newPassword: form.newPassword.value,
        newPasswordConfirm: form.newPasswordConfirm.value
      }).then(function (result) {
        if (result.ok) {
          form.reset();
          status.textContent = '비밀번호를 바꿨어요. 다른 기기에서는 로그아웃됐어요';
          return;
        }
        var code = result.body && result.body.code;
        var field = code === 'PASSWORD_SAME_AS_CURRENT' ? 'newPassword' : 'currentPassword';
        showErrors(form, result.body, field);
      }).catch(function () { status.textContent = '바꾸지 못했어요. 잠시 후 다시 시도해 주세요'; });
    });
  }

  function initVisibility() {
    var form = document.getElementById('visibility-form');
    if (!form) { return; }
    var status = document.getElementById('visibility-status');
    form.addEventListener('change', function (e) {
      if (e.target.name !== 'defaultVisibility') { return; }
      clearErrors(form);
      send('PATCH', '/api/me/settings', { defaultVisibility: e.target.value }).then(function (result) {
        if (result.ok) { status.textContent = '저장했어요'; } else { showErrors(form, result.body, 'defaultVisibility'); }
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    initProfile();
    initPassword();
    initVisibility();
  });
})();

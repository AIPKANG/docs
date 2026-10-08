/* 설정 화면의 사진 저장 공간 사용량(008 FR-027). GET /api/me/storage → 글자로만 넣는다. */
(function () {
  'use strict';

  function gb(bytes) { return (bytes / 1073741824).toFixed(2) + 'GB'; }

  function init() {
    var target = document.getElementById('storage-usage');
    if (!target) { return; }
    fetch('/api/me/storage', { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (u) {
        if (!u) { target.textContent = '사용량을 불러오지 못했어요.'; return; }
        target.textContent = '저장 공간 ' + gb(u.usedBytes) + ' / ' + gb(u.quotaBytes) + ' · 오늘 올린 사진 '
          + u.todayCount + ' / ' + u.dailyLimit + '장';
        if (u.usedBytes * 10 >= u.quotaBytes * 9) { target.textContent += ' (90% 넘게 썼어요)'; }
      }).catch(function () { target.textContent = '사용량을 불러오지 못했어요.'; });
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

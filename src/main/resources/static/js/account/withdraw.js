/* 탈퇴 화면(023 FR-005): 확인 체크와 본인 확인 입력이 모두 있어야 [탈퇴하기]가 켜진다. 서버도 다시 검사한다. */
(function () {
  'use strict';
  var confirm = document.getElementById('withdraw-confirm');
  var input = document.getElementById('withdraw-verification');
  var submit = document.getElementById('withdraw-submit');
  if (!confirm || !input || !submit) { return; }
  function sync() { submit.disabled = !(confirm.checked && input.value.trim().length > 0); }
  confirm.addEventListener('change', sync);
  input.addEventListener('input', sync);
  sync();
})();

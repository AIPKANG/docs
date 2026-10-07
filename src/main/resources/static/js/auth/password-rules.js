/*
 * 비밀번호 규칙별 충족 여부 표시(FR-015). 색만으로 구분하지 않고 글자와 기호(✓/✗)로 보여준다.
 * 마크업: <input data-password-rules="#목록id" data-email-source="#email"> + <li data-rule="length|letter|digit|special|allowed|email">
 * 최종 판정은 서버 PasswordPolicy가 한다(흔한 비밀번호 목록은 서버에서만 검사).
 */
(function () {
  'use strict';

  var SPECIALS = "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|`~";

  function isAllowed(ch) {
    return /[A-Za-z0-9]/.test(ch) || SPECIALS.indexOf(ch) >= 0;
  }

  function evaluate(pw, email) {
    var chars = Array.from(pw);
    var local = '';
    if (email) {
      var at = email.trim().toLowerCase().lastIndexOf('@');
      if (at > 0) { local = email.trim().toLowerCase().substring(0, at); }
    }
    return {
      length: chars.length >= 8 && chars.length <= 16,
      letter: /[A-Za-z]/.test(pw),
      digit: /[0-9]/.test(pw),
      special: chars.some(function (c) { return SPECIALS.indexOf(c) >= 0; }),
      allowed: pw.length > 0 && chars.every(isAllowed),
      email: pw.length > 0 && !(local.length >= 3 && pw.toLowerCase().indexOf(local) >= 0)
    };
  }

  function bind(input) {
    var list = document.querySelector(input.dataset.passwordRules);
    var emailInput = document.querySelector(input.dataset.emailSource || '');
    if (!list) { return; }
    var items = list.querySelectorAll('li[data-rule]');
    var labels = [];
    for (var i = 0; i < items.length; i++) { labels.push(items[i].textContent); }

    function update() {
      var result = evaluate(input.value, emailInput ? emailInput.value : '');
      for (var i = 0; i < items.length; i++) {
        var ok = result[items[i].dataset.rule];
        items[i].dataset.state = ok ? 'ok' : 'todo';
        items[i].textContent = (ok ? '✓ ' : '✗ ') + labels[i] + (ok ? ' (충족)' : ' (미충족)');
      }
    }

    input.addEventListener('input', update);
    if (emailInput) { emailInput.addEventListener('input', update); }
    update();
  }

  function init() {
    var inputs = document.querySelectorAll('input[data-password-rules]');
    for (var i = 0; i < inputs.length; i++) { bind(inputs[i]); }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

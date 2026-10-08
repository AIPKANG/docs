/*
 * 종 아이콘(017 FR-024·FR-025): 열 때와 30초마다 안 읽은 수만 확인(탭이 가려지면 멈추고, 보이면 바로 한 번). 99개 초과 "99+",
 * 화면 낭독기 이름 "안 읽은 알림 N개". 누르면 최근 10개 펼침 목록과 [모든 알림 보기]. 글자는 textContent로만 넣는다.
 */
(function () {
  'use strict';

  var bell = document.getElementById('notif-bell');
  var badge = document.getElementById('notif-badge');
  var panel = document.getElementById('notif-panel');
  if (!bell || !badge || !panel) { return; }
  var POLL_MS = 30000;
  var timer = null;

  function csrf() {
    var t = document.querySelector('meta[name="_csrf"]'), h = document.querySelector('meta[name="_csrf_header"]');
    var headers = {};
    if (t && h) { headers[h.content] = t.content; }
    return headers;
  }

  function show(count) {
    var label = count > 99 ? '99+' : String(count);
    badge.textContent = label;
    badge.hidden = count === 0;
    bell.setAttribute('aria-label', '안 읽은 알림 ' + count + '개');
  }

  function check() {
    fetch('/api/notifications/unread-count', { credentials: 'same-origin', cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (body) { if (body) { show(body.count); } })
      .catch(function () {});
  }

  function schedule() {
    if (timer) { clearInterval(timer); timer = null; }
    if (document.visibilityState === 'visible') { timer = setInterval(check, POLL_MS); }
  }

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'visible') { check(); }
    schedule();
  });

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) { node.className = cls; }
    if (text !== undefined) { node.textContent = text; }
    return node;
  }

  function openItem(item) {
    fetch('/api/notifications/' + item.id + '/read', { method: 'PATCH', credentials: 'same-origin', headers: csrf(), keepalive: true })
      .catch(function () {})
      .then(function () {
        if (item.url) { location.href = item.url; } else { check(); load(); }
      });
  }

  function render(items) {
    panel.textContent = '';
    if (!items.length) {
      panel.appendChild(el('p', 'notice', '새 알림이 없어요'));
    } else {
      var list = el('ul', 'notif-list');
      items.forEach(function (item) {
        var li = el('li', item.read ? 'notif' : 'notif notif-unread');
        var btn = el('button', 'notif-main');
        btn.type = 'button';
        if (!item.read) { btn.appendChild(el('span', 'notif-dot', '●')).setAttribute('aria-hidden', 'true'); btn.appendChild(el('span', 'visually-hidden', '안 읽음')); }
        btn.appendChild(el('span', 'notif-message', item.message));
        btn.appendChild(el('span', 'notif-time', item.timeLabel));
        btn.addEventListener('click', function () { openItem(item); });
        li.appendChild(btn);
        list.appendChild(li);
      });
      panel.appendChild(list);
    }
    var all = el('a', null, '모든 알림 보기');
    all.href = '/notifications';
    panel.appendChild(el('p')).appendChild(all);
  }

  function load() {
    panel.textContent = '불러오는 중…';
    fetch('/api/notifications?size=10', { credentials: 'same-origin', cache: 'no-store' })
      .then(function (r) { if (!r.ok) { throw new Error(); } return r.json(); })
      .then(function (body) { render(body.items || []); })
      .catch(function () { panel.textContent = '알림을 불러오지 못했어요'; });
  }

  bell.addEventListener('click', function (e) {
    e.preventDefault();
    var open = panel.hidden;
    panel.hidden = !open;
    bell.setAttribute('aria-expanded', String(open));
    if (open) { load(); }
  });
  document.addEventListener('click', function (e) {
    if (!panel.hidden && !panel.contains(e.target) && e.target !== bell && !bell.contains(e.target)) {
      panel.hidden = true;
      bell.setAttribute('aria-expanded', 'false');
    }
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !panel.hidden) { panel.hidden = true; bell.setAttribute('aria-expanded', 'false'); bell.focus(); }
  });

  check();
  schedule();
})();

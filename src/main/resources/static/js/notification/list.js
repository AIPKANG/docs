/*
 * 전체 알림 페이지(017 FR-026): [더 보기] 링크를 가로채 20개씩 붙이고, 이미 있는 알림 번호는 건너뛴다(묶음이 위로 올라가도
 * 중복 없음). 삭제·읽음 폼은 스크립트 없이도 동작한다. 글자는 textContent로만 넣는다.
 */
(function () {
  'use strict';

  var list = document.getElementById('notif-list');
  var more = document.getElementById('notif-more');
  var status = document.getElementById('notif-status');
  if (!list || !more) { return; }
  var token = document.querySelector('meta[name="_csrf"]');
  var param = document.querySelector('meta[name="_csrf_parameter"]');

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) { node.className = cls; }
    if (text !== undefined) { node.textContent = text; }
    return node;
  }

  function form(action, cls) {
    var f = el('form', cls);
    f.method = 'post';
    f.action = action;
    if (token) {
      var input = el('input');
      input.type = 'hidden';
      input.name = param ? param.content : '_csrf';
      input.value = token.content;
      f.appendChild(input);
    }
    return f;
  }

  function item(n) {
    var li = el('li', n.read ? 'notif' : 'notif notif-unread');
    li.dataset.id = n.id;
    var open = form('/notifications/' + n.id + '/open', 'notif-open');
    var btn = el('button', 'notif-main');
    btn.type = 'submit';
    if (!n.read) { btn.appendChild(el('span', 'notif-dot', '●')).setAttribute('aria-hidden', 'true'); btn.appendChild(el('span', 'visually-hidden', '안 읽음')); }
    btn.appendChild(el('span', 'notif-message', n.message));
    btn.appendChild(el('span', 'notif-time', n.timeLabel));
    open.appendChild(btn);
    var del = form('/notifications/' + n.id + '/delete', 'notif-delete');
    var x = el('button', null, '×');
    x.type = 'submit';
    x.setAttribute('aria-label', '알림 삭제');
    del.appendChild(x);
    li.appendChild(open);
    li.appendChild(del);
    return li;
  }

  more.addEventListener('click', function (e) {
    e.preventDefault();
    if (more.getAttribute('aria-disabled') === 'true') { return; }
    more.setAttribute('aria-disabled', 'true');
    status.textContent = '불러오는 중…';
    fetch('/api/notifications?size=20&cursor=' + encodeURIComponent(more.dataset.cursor), { credentials: 'same-origin', cache: 'no-store' })
      .then(function (r) { if (!r.ok) { throw new Error(); } return r.json(); })
      .then(function (body) {
        (body.items || []).forEach(function (n) {
          if (!list.querySelector('[data-id="' + n.id + '"]')) { list.appendChild(item(n)); }
        });
        status.textContent = '';
        if (body.nextCursor) {
          more.dataset.cursor = body.nextCursor;
          more.href = '/notifications?cursor=' + encodeURIComponent(body.nextCursor);
          more.removeAttribute('aria-disabled');
        } else {
          more.parentNode.removeChild(more);
          status.textContent = '모든 알림을 다 봤어요';
        }
      })
      .catch(function () {
        status.textContent = '불러오지 못했어요';
        more.removeAttribute('aria-disabled');
      });
  });
})();

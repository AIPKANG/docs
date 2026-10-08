/*
 * 댓글 영역(014). 작성·답글·수정·삭제는 API를 부른 뒤 그 댓글 위치로 다시 연다(?comment=id). 답글 더 보기는 API로 받아 붙인다.
 * 사용자 글은 textContent로만 넣는다. 스크립트가 없으면 폼 제출과 [댓글 더 보기] 링크가 그대로 동작한다.
 */
(function () {
  'use strict';

  function csrfHeaders() {
    var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) { headers[header.content] = token.content; }
    return headers;
  }

  function call(method, url, body) {
    return fetch(url, { method: method, headers: csrfHeaders(), credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body) })
      .then(function (res) {
        return res.text().then(function (t) {
          var data = {};
          try { data = t ? JSON.parse(t) : {}; } catch (e) { data = {}; }
          if (!res.ok) {
            var err = new Error(String(res.status));
            err.message = res.status === 429 ? '잠시 후 다시 시도해 주세요' : (data.message || '처리하지 못했어요');
            throw err;
          }
          return data;
        });
      });
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) { n.className = cls; }
    if (text !== undefined && text !== null) { n.textContent = text; }
    return n;
  }

  function dateLabel(iso) {
    var at = new Date(iso), min = Math.floor((Date.now() - at) / 60000);
    if (min < 1) { return '방금 전'; }
    if (min < 60) { return min + '분 전'; }
    if (min < 1440) { return Math.floor(min / 60) + '시간 전'; }
    var k = new Date(at.getTime() + 9 * 3600000);
    function p(n) { return (n < 10 ? '0' : '') + n; }
    return k.getUTCFullYear() + '.' + p(k.getUTCMonth() + 1) + '.' + p(k.getUTCDate());
  }

  /* fragments/comment.html과 같은 모양 */
  function render(c) {
    var box = el('div', 'comment');
    box.id = 'comment-' + c.id;
    box.dataset.commentId = c.id;
    box.dataset.rootId = c.parentId || c.id;
    if (c.state === 'WITHDRAWN_AUTHOR') { box.appendChild(el('p', 'comment-gone', '탈퇴한 사용자 · 탈퇴한 사용자의 댓글이에요')); return box; }
    if (c.state === 'DELETED') { box.appendChild(el('p', 'comment-gone', '삭제된 댓글이에요')); return box; }
    if (c.state === 'HIDDEN' && !c.mine) { box.appendChild(el('p', 'comment-gone', '운영 정책에 따라 숨겨진 댓글이에요')); return box; }
    var head = el('p', 'comment-head');
    var a = el('a', 'comment-author'); a.href = '/@' + c.author.handle;
    a.appendChild(el('span', null, c.author.nickname)); a.appendChild(document.createTextNode(' '));
    a.appendChild(el('span', 'help', '@' + c.author.handle));
    head.appendChild(a);
    if (c.author.isPostAuthor) { head.appendChild(el('span', 'badge', '작성자')); }
    var t = el('time', 'help', dateLabel(c.createdAt)); t.setAttribute('datetime', c.createdAt); head.appendChild(t);
    if (c.edited) { head.appendChild(el('span', 'help', ' · 수정됨')); }
    box.appendChild(head);
    if (c.state === 'HIDDEN') { box.appendChild(el('p', 'warning', '운영 정책에 따라 숨겨졌어요 (나만 보여요)')); }
    if (c.replyTo) { box.appendChild(el('p', 'reply-to', '@' + c.replyTo.nickname + '에게')); }
    box.appendChild(el('p', 'comment-content', c.content));
    var actions = el('p', 'comment-actions');
    if (c.canReply) { actions.appendChild(el('button', 'comment-reply', '답글')); }
    if (c.canEdit) { actions.appendChild(el('button', 'comment-edit', '수정')); }
    if (c.canDelete) { actions.appendChild(el('button', 'comment-delete', '삭제')); }
    if (c.canReport) { var r = el('button', 'comment-report', '신고'); r.dataset.reportCommentId = c.id; actions.appendChild(r); }
    Array.prototype.forEach.call(actions.querySelectorAll('button'), function (b) { b.type = 'button'; });
    box.appendChild(actions);
    var error = el('p', 'field-error comment-error'); error.hidden = true; error.setAttribute('role', 'alert');
    box.appendChild(error);
    return box;
  }

  function init() {
    var section = document.getElementById('comments');
    if (!section || !section.dataset.postId) { return; }
    var postId = section.dataset.postId;
    var base = location.pathname;

    function reopen(id) { window.location.assign(base + '?comment=' + id + '#comment-' + id); }

    function showError(box, message) {
      var e = box.querySelector('.comment-error') || document.getElementById('comment-form-error');
      if (e) { e.textContent = message; e.hidden = false; }
    }

    var form = document.getElementById('comment-form');
    if (form) {
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        var text = form.querySelector('textarea').value;
        call('POST', '/api/posts/' + postId + '/comments', { content: text })
          .then(function (c) { reopen(c.id); })
          .catch(function (err) { var x = document.getElementById('comment-form-error'); x.textContent = err.message; x.hidden = false; });
      });
    }

    function inlineForm(box, initial, label, onSubmit) {
      var existing = box.querySelector('.comment-inline-form');
      if (existing) { existing.remove(); return; }
      var f = el('form', 'comment-inline-form');
      var ta = el('textarea'); ta.maxLength = 1000; ta.value = initial || ''; ta.setAttribute('aria-label', label);
      var ok = el('button', null, label); ok.type = 'submit';
      var cancel = el('button', null, '취소'); cancel.type = 'button';
      cancel.addEventListener('click', function () { f.remove(); });
      f.appendChild(ta); f.appendChild(ok); f.appendChild(cancel);
      f.addEventListener('submit', function (e) { e.preventDefault(); onSubmit(ta.value); });
      box.appendChild(f);
      ta.focus();
    }

    section.addEventListener('click', function (e) {
      var button = e.target.closest('button');
      if (!button) { return; }
      var box = button.closest('.comment');
      if (button.classList.contains('comment-reply')) {
        inlineForm(box, '', '답글 쓰기', function (text) {
          call('POST', '/api/posts/' + postId + '/comments', { content: text, replyToCommentId: Number(box.dataset.commentId) })
            .then(function (c) { reopen(c.id); }).catch(function (err) { showError(box, err.message); });
        });
      } else if (button.classList.contains('comment-edit')) {
        var current = box.querySelector('.comment-content').textContent;
        inlineForm(box, current, '수정', function (text) {
          call('PATCH', '/api/comments/' + box.dataset.commentId, { content: text })
            .then(function (c) { reopen(c.id); }).catch(function (err) { showError(box, err.message); });
        });
      } else if (button.classList.contains('comment-delete')) {
        if (!window.confirm('댓글을 삭제할까요? 되돌릴 수 없어요')) { return; }
        call('DELETE', '/api/comments/' + box.dataset.commentId)
          .then(function () { window.location.assign(base + '#comments'); window.location.reload(); })
          .catch(function (err) { showError(box, err.message); });
      } else if (button.classList.contains('replies-more')) {
        button.disabled = true;
        call('GET', '/api/comments/' + button.dataset.rootId + '/replies?cursor=' + encodeURIComponent(button.dataset.cursor))
          .then(function (page) {
            var list = button.parentNode.querySelector('.reply-list');
            (page.items || []).forEach(function (c) {
              if (document.getElementById('comment-' + c.id)) { return; }
              var li = el('li'); li.appendChild(render(c)); list.appendChild(li);
            });
            if (page.nextCursor) { button.dataset.cursor = page.nextCursor; button.disabled = false; button.textContent = '답글 더 보기'; }
            else { button.remove(); }
          }).catch(function () { button.disabled = false; });
      }
    });

    // 특정 댓글로 들어왔으면 스크롤 + 잠깐 강조(FR-020)
    var target = section.dataset.commentTarget && document.getElementById('comment-' + section.dataset.commentTarget);
    if (target) {
      target.scrollIntoView({ block: 'center' });
      target.classList.add('highlight');
      setTimeout(function () { target.classList.remove('highlight'); }, 2500);
    }
  }

  window.blogComments = { render: render };
  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

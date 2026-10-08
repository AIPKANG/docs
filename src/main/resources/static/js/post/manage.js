/*
 * 내 글 관리 버튼(011 FR-009·FR-013·FR-018·FR-019·FR-027, 006 공개 범위). 폼 제출을 가로채 API를 부르고 그 줄만 바꾸거나 지운다.
 * 실패하면 그 줄 아래 이유. 스크립트가 없으면 폼이 그대로 제출되어 같은 탭으로 돌아온다.
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

  function rowOf(el) { return el.closest('.post-row'); }

  function fail(row, text) {
    var box = row.querySelector('.row-error');
    box.textContent = text;
    box.hidden = false;
  }

  function call(method, url, body) {
    return fetch(url, { method: method, headers: csrfHeaders(), credentials: 'same-origin',
      body: body ? JSON.stringify(body) : undefined })
      .then(function (res) {
        return res.text().then(function (t) {
          var data = {};
          try { data = t ? JSON.parse(t) : {}; } catch (e) { data = {}; }
          if (!res.ok) { var err = new Error(String(res.status)); err.data = data; throw err; }
          return data;
        });
      });
  }

  function notice(text) {
    var box = document.getElementById('manage-notice');
    box.textContent = text;
    box.hidden = !text;
  }

  var CONFIRM = {
    trash: '휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요',
    purge: '영구 삭제하면 되돌릴 수 없어요. 댓글·좋아요도 함께 지워져요'
  };

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll('form[data-row-action]'), function (form) {
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        var action = form.dataset.rowAction;
        var row = rowOf(form);
        var id = row.dataset.postId;
        if (CONFIRM[action] && !window.confirm(CONFIRM[action])) { return; }
        var request = action === 'trash' ? call('DELETE', '/api/posts/' + id)
          : action === 'restore' ? call('POST', '/api/posts/' + id + '/restore')
          : call('DELETE', '/api/posts/' + id + '/permanent');
        request.then(function (data) {
          if (data && data.purged) { notice('빈 글이라 바로 삭제했어요'); }
          if (action === 'restore') { notice('복구했어요. 발행 글 탭이나 임시글 탭에서 볼 수 있어요'); }
          row.remove();
        }).catch(function (err) {
          fail(row, (err.data && err.data.message) || '처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.');
        });
      });
    });

    Array.prototype.forEach.call(document.querySelectorAll('.visibility-toggle'), function (button) {
      button.addEventListener('click', function () {
        var row = rowOf(button);
        var to = button.dataset.visibility !== 'PUBLIC' ? 'PUBLIC' : 'PRIVATE'; // 친구 공개(025)도 공개 쪽으로
        // 026 강성찬 개인 확장: 넓히는 쪽(→ 전체 공개)만 확인(41 §4)
        if (to === 'PUBLIC' && !window.confirm('모든 사람이 볼 수 있게 돼요. 공개할까요?')) { return; }
        button.disabled = true;
        call('PATCH', '/api/posts/' + button.dataset.postId + '/visibility', { visibility: to }).then(function (data) {
          button.disabled = false;
          button.dataset.visibility = data.visibility;
          button.textContent = data.visibility !== 'PUBLIC' ? '전체 공개로' : '나만 보기로';
          var badge = row.querySelector('[data-visibility-badge]');
          if (badge) {
            badge.dataset.visibility = data.visibility;
            badge.children[0].textContent = data.visibility === 'PRIVATE' ? '🔒' : (data.visibility === 'FRIENDS' ? '👥' : '🌐');
            badge.children[1].textContent = data.visibility === 'PRIVATE' ? '비공개' : (data.visibility === 'FRIENDS' ? '친구 공개' : '공개');
          }
        }).catch(function (err) {
          button.disabled = false;
          fail(row, (err.data && err.data.message) || '공개 범위를 바꾸지 못했어요.');
        });
      });
    });

    Array.prototype.forEach.call(document.querySelectorAll('.discard-editing'), function (button) {
      button.addEventListener('click', function () {
        if (!window.confirm('고치던 내용을 버리고 마지막 발행본으로 되돌릴까요?')) { return; }
        var row = rowOf(button);
        call('DELETE', '/api/posts/' + button.dataset.postId + '/working-copy').then(function () {
          var badge = row.querySelector('.badge-editing');
          if (badge) { badge.remove(); }
          button.remove();
        }).catch(function () { fail(row, '변경을 취소하지 못했어요.'); });
      });
    });
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

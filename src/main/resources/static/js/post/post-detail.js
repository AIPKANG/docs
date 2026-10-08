/*
 * 글 상세의 작성자 버튼(010 FR-016·FR-019): 공개 범위 즉시 변경(006 PATCH), 수정 중 안내의 [변경 취소](004 DELETE).
 * [삭제]는 확인창 뒤 휴지통으로 옮기고 내 글 관리 휴지통 탭으로 간다(011). 인라인 스크립트 없이 data 속성으로 글 번호를 읽는다(FR-023).
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

  function init() {
    var article = document.querySelector('article.post[data-post-id]');
    if (!article) { return; }
    var postId = article.dataset.postId;
    var status = document.getElementById('author-action-status');
    var select = document.getElementById('visibility-select');
    if (select) {
      var previous = select.value;
      select.addEventListener('change', function () {
        // 026 강성찬 개인 확장: 넓히는 쪽(→ 전체 공개)만 확인(41 §4)
        if (select.value === 'PUBLIC' && previous !== 'PUBLIC' && !window.confirm('모든 사람이 볼 수 있게 돼요. 공개할까요?')) {
          select.value = previous;
          return;
        }
        fetch('/api/posts/' + postId + '/visibility', { method: 'PATCH', headers: csrfHeaders(), credentials: 'same-origin',
          body: JSON.stringify({ visibility: select.value }) })
          .then(function (res) {
            if (!res.ok) { throw new Error(String(res.status)); }
            previous = select.value;
            status.textContent = select.value === 'PRIVATE' ? '나만 볼 수 있게 바꿨어요.' : (select.value === 'FRIENDS' ? '친구만 볼 수 있게 바꿨어요.' : (select.value === 'LINK' ? '링크를 아는 사람만 볼 수 있게 바꿨어요.' : '전체 공개로 바꿨어요.'));
            window.location.reload();
          })
          .catch(function () { select.value = previous; status.textContent = '공개 범위를 바꾸지 못했어요.'; });
      });
    }
    var trash = document.getElementById('trash-post');
    if (trash) {
      trash.addEventListener('click', function () {
        if (!window.confirm('휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요')) { return; }
        fetch('/api/posts/' + postId, { method: 'DELETE', headers: csrfHeaders(), credentials: 'same-origin' })
          .then(function (res) {
            if (!res.ok) { throw new Error(String(res.status)); }
            window.location.assign('/manage/posts?tab=trash');
          })
          .catch(function () { status.textContent = '삭제하지 못했어요.'; });
      });
    }
    var discard = document.getElementById('discard-editing');
    if (discard) {
      discard.addEventListener('click', function () {
        if (!window.confirm('고치던 내용을 버리고 마지막 발행본으로 되돌릴까요?')) { return; }
        fetch('/api/posts/' + postId + '/working-copy', { method: 'DELETE', headers: csrfHeaders(), credentials: 'same-origin' })
          .then(function (res) { if (res.status === 204) { window.location.reload(); } });
      });
    }
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

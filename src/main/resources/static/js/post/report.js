/*
 * 신고(022 FR-001~FR-011): 글의 [신고]와 댓글의 [신고]가 같은 창을 연다. 사유 하나, 기타는 설명 필수(200자).
 * 같은 대상을 다시 신고해도 "신고했어요"로 끝난다. 결과·신고 수는 보여 주지 않는다. 글자는 textContent로만.
 */
(function () {
  'use strict';

  var dialog = document.getElementById('report-dialog');
  var form = document.getElementById('report-form');
  var error = document.getElementById('report-error');
  var status = document.getElementById('report-status');
  if (!dialog || !form) { return; }
  var target = null;

  function headers() {
    var h = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    var t = document.querySelector('meta[name="_csrf"]'), n = document.querySelector('meta[name="_csrf_header"]');
    if (t && n) { h[n.content] = t.content; }
    return h;
  }

  document.addEventListener('click', function (e) {
    var b = e.target.closest ? e.target.closest('[data-report-post-id], [data-report-comment-id]') : null;
    if (!b) { return; }
    target = b.dataset.reportPostId ? { targetType: 'POST', targetId: Number(b.dataset.reportPostId) }
      : { targetType: 'COMMENT', targetId: Number(b.dataset.reportCommentId) };
    form.reset();
    error.textContent = '';
    dialog.showModal();
  });

  document.getElementById('report-cancel').addEventListener('click', function () { dialog.close(); });

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    var reason = form.querySelector('input[name="reason"]:checked');
    var detail = form.querySelector('#report-detail').value.trim();
    if (!reason) { error.textContent = '신고 사유를 골라 주세요'; return; }
    if (reason.value === 'OTHER' && !detail) { error.textContent = '기타 사유는 설명을 적어 주세요'; return; }
    fetch('/api/reports', {
      method: 'POST', credentials: 'same-origin', headers: headers(),
      body: JSON.stringify({ targetType: target.targetType, targetId: target.targetId, reason: reason.value, detail: detail || null })
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (body) { return { status: r.status, body: body }; });
    }).then(function (res) {
      if (res.status === 200 || res.status === 201) {
        dialog.close();
        status.textContent = '신고했어요. 운영자가 확인할게요';
        return;
      }
      var first = res.body.errors && res.body.errors[0];
      error.textContent = (first && first.message) || res.body.message || '잠시 후 다시 시도해 주세요';
    }).catch(function () { error.textContent = '잠시 후 다시 시도해 주세요'; });
  });
})();

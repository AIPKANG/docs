/*
 * [더 보기](009 FR-004~FR-009, 10 §4). 링크(?cursor=)를 가로채 API로 9개를 받아 붙인다. 이미 있는 글 번호는 건너뛴다.
 * 불러오는 중 "불러오는 중…" 비활성, 실패 "불러오지 못했어요 [다시 시도]"(같은 커서), 끝 "모든 글을 다 봤어요".
 * 글을 보러 떠날 때 붙인 카드·다음 커서·스크롤을 sessionStorage에 30분 보관하고, 같은 주소로 돌아오면 복원한다.
 * 글자는 모두 textContent로 넣는다.
 */
(function () {
  'use strict';

  var RESTORE_MS = 30 * 60 * 1000;

  function dateLabel(iso) {
    var at = new Date(iso), now = new Date(), min = Math.floor((now - at) / 60000);
    if (min < 1) { return '방금 전'; }
    if (min < 60) { return min + '분 전'; }
    if (min < 24 * 60) { return Math.floor(min / 60) + '시간 전'; }
    var kst = new Date(at.getTime() + 9 * 3600 * 1000);
    function pad(n) { return (n < 10 ? '0' : '') + n; }
    return kst.getUTCFullYear() + '.' + pad(kst.getUTCMonth() + 1) + '.' + pad(kst.getUTCDate());
  }

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) { node.className = cls; }
    if (text !== undefined) { node.textContent = text; }
    return node;
  }

  var COLORS = ['#0B5CAD', '#1A7F37', '#8250DF', '#BF3989', '#9A6700', '#CF222E', '#0E7490', '#57606A'];
  function hash(s) { var h = 0; for (var i = 0; i < s.length; i++) { h = (31 * h + s.charCodeAt(i)) | 0; } return h; }

  function avatar(author) {
    if (author.profileImageUrl) {
      var img = el('img', 'avatar'); img.src = author.profileImageUrl; img.alt = ''; img.width = 24; img.height = 24;
      return img;
    }
    var span = el('span', 'avatar avatar-default', (author.nickname || '?').charAt(0).toUpperCase());
    span.setAttribute('aria-hidden', 'true');
    span.style.cssText = 'width:24px;height:24px;font-size:12px;background:' + COLORS[((hash(author.handle || '') % 8) + 8) % 8];
    return span;
  }

  function card(item, showAuthor) {
    var article = el('article', 'post-card');
    article.dataset.postId = item.id;
    var thumb = el('div', 'card-thumb');
    if (item.thumbnailUrl) { var img = el('img'); img.src = item.thumbnailUrl; img.alt = item.title; img.loading = 'lazy'; thumb.appendChild(img); }
    article.appendChild(thumb);
    var body = el('div', 'card-body');
    var h2 = el('h2', 'card-title'); var link = el('a', 'card-link', item.title); link.href = item.url; link.title = item.title;
    h2.appendChild(link); body.appendChild(h2);
    body.appendChild(el('p', 'card-excerpt', item.excerpt || ''));
    var meta = el('p', 'card-meta'); var time = el('time', null, dateLabel(item.firstPublicAt)); time.setAttribute('datetime', item.firstPublicAt);
    meta.appendChild(time); meta.appendChild(document.createTextNode(' · 💬 ' + item.commentCount)); body.appendChild(meta);
    var foot = el('p', 'card-foot');
    if (showAuthor) {
      var a = el('a', 'card-author'); a.href = '/@' + item.author.handle;
      a.appendChild(avatar(item.author)); a.appendChild(el('span', null, item.author.nickname || ''));
      foot.appendChild(a);
    }
    foot.appendChild(el('span', 'card-likes', '♥ ' + item.likeCount));
    body.appendChild(foot);
    article.appendChild(body);
    return article;
  }

  function init() {
    var box = document.querySelector('.list-more');
    var grid = document.getElementById('card-grid');
    if (!box || !grid) { return; }
    var api = box.dataset.listApi;
    var showAuthor = box.dataset.showAuthor === 'true';
    var storeKey = 'list:' + location.pathname + location.search;
    var appended = [];
    var next = (box.querySelector('.more-button') || {}).dataset ? (box.querySelector('.more-button') || {}).dataset.cursor : null;
    var errorBox = box.querySelector('.list-error');

    function seen() {
      var ids = {};
      Array.prototype.forEach.call(grid.querySelectorAll('[data-post-id]'), function (n) { ids[n.dataset.postId] = true; });
      return ids;
    }

    function render() {
      Array.prototype.forEach.call(box.querySelectorAll('.more-button, .list-end'), function (n) { n.remove(); });
      if (next) {
        var more = el('a', 'more-button', '더 보기'); more.href = (box.dataset.linkBase || '?cursor=') + next; more.dataset.cursor = next;
        more.addEventListener('click', load); box.insertBefore(more, errorBox);
      } else if (grid.children.length) {
        box.insertBefore(el('p', 'help list-end', '모든 글을 다 봤어요'), errorBox);
      }
    }

    function append(items) {
      var ids = seen();
      items.forEach(function (item) {
        if (ids[item.id]) { return; }
        grid.appendChild(card(item, showAuthor));
        appended.push(item);
      });
    }

    function load(e) {
      if (e) { e.preventDefault(); }
      var button = box.querySelector('.more-button');
      if (button) { button.textContent = '불러오는 중…'; button.setAttribute('aria-disabled', 'true'); button.style.pointerEvents = 'none'; }
      errorBox.hidden = true;
      fetch(api + (api.indexOf('?') >= 0 ? '&' : '?') + 'cursor=' + encodeURIComponent(next), { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
        .then(function (res) {
          // 019: 보던 트렌딩 순위가 사라짐 → 안내와 함께 처음부터
          if (res.status === 410 && box.dataset.expiredUrl) { location.href = box.dataset.expiredUrl; return { items: [], nextCursor: null }; }
          if (!res.ok) { throw new Error(String(res.status)); }
          return res.json();
        })
        .then(function (page) { append(page.items || []); next = page.nextCursor; render(); })
        .catch(function () {
          if (button) { button.textContent = '더 보기'; button.removeAttribute('aria-disabled'); button.style.pointerEvents = ''; }
          errorBox.hidden = false;
        });
    }

    errorBox.querySelector('.list-retry').addEventListener('click', function () { load(); });
    var first = box.querySelector('.more-button');
    if (first) { first.addEventListener('click', load); }

    // 복원(30분)
    try {
      var saved = JSON.parse(sessionStorage.getItem(storeKey) || 'null');
      if (saved && Date.now() - saved.savedAt < RESTORE_MS && saved.items && saved.items.length) {
        append(saved.items);
        next = saved.next;
        render();
        window.scrollTo(0, saved.scrollY || 0);
      }
      sessionStorage.removeItem(storeKey);
    } catch (err) { /* 보관값이 깨졌으면 처음부터 */ }

    window.addEventListener('pagehide', function () {
      if (!appended.length) { return; }
      try {
        sessionStorage.setItem(storeKey, JSON.stringify({ items: appended, next: next, scrollY: window.scrollY, savedAt: Date.now() }));
      } catch (err) { /* 저장 공간이 없으면 복원하지 않음 */ }
    });
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', init); } else { init(); }
})();

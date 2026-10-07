/*
 * 비교 창 diff(004 FR-020, research R-10). 의존성 없는 순수 함수:
 * - rows(a, b): 줄 단위 LCS 결과를 좌우 행으로 맞춘다. 바뀐 줄 쌍(change)은 단어 단위로 다시 비교한다.
 * - words(a, b): 공백 경계 단어 LCS. 각 쪽 조각 {text, changed}.
 * - fold(rows, min, keep): 바뀌지 않은 긴 구간(min줄 이상)을 앞뒤 keep줄만 남기고 접는다.
 * 브라우저에서는 window.blogDiff, Node에서는 module.exports(src/test/js/diff.test.mjs).
 */
(function (root) {
  'use strict';

  var MAX_CELLS = 4000000; // 이보다 크면 가운데를 통째로 지움+추가로 본다(화면 멈춤 방지)

  function lcsOps(a, b) {
    var start = 0;
    while (start < a.length && start < b.length && a[start] === b[start]) { start++; }
    var endA = a.length, endB = b.length;
    while (endA > start && endB > start && a[endA - 1] === b[endB - 1]) { endA--; endB--; }
    var ops = [];
    var i;
    for (i = 0; i < start; i++) { ops.push({ type: 'same', a: a[i], b: b[i] }); }
    var midA = a.slice(start, endA), midB = b.slice(start, endB);
    var n = midA.length, m = midB.length;
    if (n * m > MAX_CELLS) {
      midA.forEach(function (x) { ops.push({ type: 'del', a: x }); });
      midB.forEach(function (x) { ops.push({ type: 'add', b: x }); });
    } else if (n > 0 || m > 0) {
      var w = m + 1;
      var table = new Uint32Array((n + 1) * w);
      var x, y;
      for (x = n - 1; x >= 0; x--) {
        for (y = m - 1; y >= 0; y--) {
          table[x * w + y] = midA[x] === midB[y] ? table[(x + 1) * w + y + 1] + 1
            : Math.max(table[(x + 1) * w + y], table[x * w + y + 1]);
        }
      }
      x = 0; y = 0;
      while (x < n && y < m) {
        if (midA[x] === midB[y]) { ops.push({ type: 'same', a: midA[x], b: midB[y] }); x++; y++; }
        else if (table[(x + 1) * w + y] >= table[x * w + y + 1]) { ops.push({ type: 'del', a: midA[x] }); x++; }
        else { ops.push({ type: 'add', b: midB[y] }); y++; }
      }
      while (x < n) { ops.push({ type: 'del', a: midA[x++] }); }
      while (y < m) { ops.push({ type: 'add', b: midB[y++] }); }
    }
    for (i = endA; i < a.length; i++) { ops.push({ type: 'same', a: a[i], b: b[i - endA + endB] }); }
    return ops;
  }

  function tokens(text) {
    return text.split(/(\s+)/).filter(function (t) { return t.length > 0; });
  }

  function words(a, b) {
    var ops = lcsOps(tokens(a), tokens(b));
    var left = [], right = [];
    ops.forEach(function (op) {
      if (op.type === 'same') {
        left.push({ text: op.a, changed: false });
        right.push({ text: op.b, changed: false });
      } else if (op.type === 'del') {
        left.push({ text: op.a, changed: true });
      } else {
        right.push({ text: op.b, changed: true });
      }
    });
    return { left: merge(left), right: merge(right) };
  }

  function merge(parts) {
    var out = [];
    parts.forEach(function (p) {
      var last = out[out.length - 1];
      if (last && last.changed === p.changed) { last.text += p.text; } else { out.push({ text: p.text, changed: p.changed }); }
    });
    return out;
  }

  function splitLines(text) {
    return (text || '').replace(/\r\n?/g, '\n').split('\n');
  }

  /* 행: {type: same|change|del|add, left, right, leftParts?, rightParts?} */
  function rows(a, b) {
    var ops = lcsOps(splitLines(a), splitLines(b));
    var out = [];
    var i = 0;
    while (i < ops.length) {
      if (ops[i].type === 'same') { out.push({ type: 'same', left: ops[i].a, right: ops[i].b }); i++; continue; }
      var dels = [], adds = [];
      while (i < ops.length && ops[i].type !== 'same') {
        if (ops[i].type === 'del') { dels.push(ops[i].a); } else { adds.push(ops[i].b); }
        i++;
      }
      var k;
      for (k = 0; k < Math.max(dels.length, adds.length); k++) {
        if (k < dels.length && k < adds.length) {
          var w = words(dels[k], adds[k]);
          out.push({ type: 'change', left: dels[k], right: adds[k], leftParts: w.left, rightParts: w.right });
        } else if (k < dels.length) {
          out.push({ type: 'del', left: dels[k], right: null });
        } else {
          out.push({ type: 'add', left: null, right: adds[k] });
        }
      }
    }
    return out;
  }

  /* 바뀌지 않은 긴 구간 접기: {type:'fold', count, rows:[…]}로 바꾼다 */
  function fold(list, min, keep) {
    var out = [];
    var i = 0;
    while (i < list.length) {
      if (list[i].type !== 'same') { out.push(list[i]); i++; continue; }
      var j = i;
      while (j < list.length && list[j].type === 'same') { j++; }
      var run = list.slice(i, j);
      var head = i === 0 ? 0 : keep;
      var tail = j === list.length ? 0 : keep;
      if (run.length >= min && run.length - head - tail > 0) {
        Array.prototype.push.apply(out, run.slice(0, head));
        out.push({ type: 'fold', count: run.length - head - tail, rows: run.slice(head, run.length - tail) });
        Array.prototype.push.apply(out, run.slice(run.length - tail));
      } else {
        Array.prototype.push.apply(out, run);
      }
      i = j;
    }
    return out;
  }

  function hasChanges(list) {
    return list.some(function (r) { return r.type !== 'same'; });
  }

  var api = { rows: rows, words: words, fold: fold, hasChanges: hasChanges };
  root.blogDiff = api;
  if (typeof module === 'object' && module.exports) { module.exports = api; }
})(typeof window !== 'undefined' ? window : globalThis);

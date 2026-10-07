// 비교 창 diff 순수 함수 자체 검사(004 T326). 실행: node src/test/js/diff.test.mjs (빌드 밖, Node만 필요)
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const diff = require('../../main/resources/static/js/editor/diff.js');

let passed = 0;
function test(name, fn) { fn(); passed++; console.log('ok -', name); }

test('같은 글은 바뀐 줄이 없다', () => {
  const rows = diff.rows('a\nb', 'a\nb');
  assert.equal(diff.hasChanges(rows), false);
  assert.deepEqual(rows.map(r => r.type), ['same', 'same']);
});

test('바뀐 줄은 change로 짝짓고 단어만 강조한다', () => {
  const rows = diff.rows('## 원인\n- fetch join으로 해결한다', '## 원인\n- EntityGraph로 해결한다');
  assert.deepEqual(rows.map(r => r.type), ['same', 'change']);
  const change = rows[1];
  assert.deepEqual(change.leftParts.filter(p => p.changed).map(p => p.text), ['fetch join으로']);
  assert.deepEqual(change.rightParts.filter(p => p.changed).map(p => p.text), ['EntityGraph로']);
});

test('추가·삭제 줄', () => {
  const rows = diff.rows('a\nb\nc', 'a\nc\nd\ne');
  assert.deepEqual(rows.map(r => r.type), ['same', 'del', 'same', 'add', 'add']);
  assert.equal(rows[1].left, 'b');
  assert.equal(rows[3].right, 'd');
});

test('빈 글과 비교', () => {
  assert.deepEqual(diff.rows('', 'x').map(r => r.type), ['change']);
  assert.deepEqual(diff.rows('x\ny', '').map(r => r.type), ['change', 'del']);
});

test('CRLF는 LF와 같게 본다', () => {
  assert.equal(diff.hasChanges(diff.rows('a\r\nb', 'a\nb')), false);
});

test('바뀌지 않은 긴 구간은 접는다(앞뒤 2줄 남김)', () => {
  const same = Array.from({ length: 10 }, (_, i) => 'l' + i).join('\n');
  const folded = diff.fold(diff.rows('x\n' + same + '\ny', 'X\n' + same + '\nY'), 6, 2);
  const fold = folded.find(r => r.type === 'fold');
  assert.equal(fold.count, 6);
  assert.equal(folded.filter(r => r.type === 'same').length, 4);
});

test('짧은 같은 구간은 접지 않는다', () => {
  const folded = diff.fold(diff.rows('x\na\nb\ny', 'X\na\nb\nY'), 6, 2);
  assert.equal(folded.some(r => r.type === 'fold'), false);
});

test('글 앞뒤의 같은 구간은 바깥쪽 줄을 남기지 않고 접는다', () => {
  const head = Array.from({ length: 8 }, (_, i) => 'h' + i).join('\n');
  const folded = diff.fold(diff.rows(head + '\nold', head + '\nnew'), 6, 2);
  assert.equal(folded[0].type, 'fold');
  assert.equal(folded[0].count, 6);
});

test('아주 큰 글도 멈추지 않는다', () => {
  const a = Array.from({ length: 3000 }, (_, i) => 'a' + i).join('\n');
  const b = Array.from({ length: 3000 }, (_, i) => 'b' + i).join('\n');
  const rows = diff.rows(a, b);
  assert.equal(rows.length, 3000);
});

console.log(`${passed} passed`);

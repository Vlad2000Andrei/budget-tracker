import test from 'node:test';
import assert from 'node:assert';
import { parseRobustDate, detectCompactDateFormat } from './dateParser.js';

test('detectCompactDateFormat identifies YYYYMMDD and YYYYDDMM correctly', () => {
  // Test case 1: unambiguous YYYYMMDD because day is 25 (> 12) in second group
  const rows1 = [
    ['20260710'], // ambiguous
    ['20260725'], // unambiguous YYYYMMDD
  ];
  assert.strictEqual(detectCompactDateFormat(rows1, 0), 'YYYYMMDD');

  // Test case 2: unambiguous YYYYDDMM because day is 25 (> 12) in first group
  const rows2 = [
    ['20261007'], // ambiguous
    ['20262507'], // unambiguous YYYYDDMM
  ];
  assert.strictEqual(detectCompactDateFormat(rows2, 0), 'YYYYDDMM');

  // Test case 3: all rows ambiguous (defaults to YYYYMMDD)
  const rows3 = [
    ['20260710'], 
    ['20260505'],
  ];
  assert.strictEqual(detectCompactDateFormat(rows3, 0), 'YYYYMMDD');
});

test('parseRobustDate parses YYYYMMDD format correctly', () => {
  const d = parseRobustDate('20260710', 'YYYYMMDD');
  assert.strictEqual(d.getFullYear(), 2026);
  assert.strictEqual(d.getMonth(), 6); // 0-based, July is 6
  assert.strictEqual(d.getDate(), 10);
});

test('parseRobustDate parses YYYYDDMM format correctly', () => {
  const d = parseRobustDate('20261007', 'YYYYDDMM');
  assert.strictEqual(d.getFullYear(), 2026);
  assert.strictEqual(d.getMonth(), 6); // 0-based, July is 6
  assert.strictEqual(d.getDate(), 10);
});

test('parseRobustDate leaves other formats untouched', () => {
  // Standard format
  const d1 = parseRobustDate('2026-07-10');
  assert.strictEqual(d1.getFullYear(), 2026);
  assert.strictEqual(d1.getMonth(), 6);
  assert.strictEqual(d1.getDate(), 10);

  // DMY format
  const d2 = parseRobustDate('25/07/2026');
  assert.strictEqual(d2.getFullYear(), 2026);
  assert.strictEqual(d2.getMonth(), 6);
  assert.strictEqual(d2.getDate(), 25);
});

import test from 'node:test';
import assert from 'node:assert/strict';

import { shouldCompareEntries } from '../check-plagiarism.mjs';

const makeEntry = (file, id, type = 'question') => ({
  file,
  id,
  type,
});

test('compares entries with same id across files by default', () => {
  const seen = new Set();
  const a = makeEntry('/tmp/a.json', 'shared');
  const b = makeEntry('/tmp/b.json', 'shared');
  assert.equal(shouldCompareEntries(a, b, { skipSameId: false }, seen), true);
});

test('skips entries with same id when skipSameId option enabled', () => {
  const seen = new Set();
  const a = makeEntry('/tmp/a.json', 'shared');
  const b = makeEntry('/tmp/b.json', 'shared');
  assert.equal(shouldCompareEntries(a, b, { skipSameId: true }, seen), false);
});

test('skips same file entries regardless of id', () => {
  const seen = new Set();
  const a = makeEntry('/tmp/a.json', 'one');
  const b = makeEntry('/tmp/a.json', 'two');
  assert.equal(shouldCompareEntries(a, b, { skipSameId: false }, seen), false);
});

test('prevents duplicate comparisons of the same file pair', () => {
  const seen = new Set();
  const a = makeEntry('/tmp/a.json', 'alpha');
  const b = makeEntry('/tmp/b.json', 'beta');
  assert.equal(shouldCompareEntries(a, b, { skipSameId: false }, seen), true);
  assert.equal(shouldCompareEntries(b, a, { skipSameId: false }, seen), false);
});

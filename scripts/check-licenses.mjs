#!/usr/bin/env node
/**
 * Minimal license checker (no deps).
 * - Verifies presence of a root LICENSE file.
 * - Basic sanity: non-empty, not a placeholder, minimal length.
 * - Optional: check third-party/vendor dirs for per-component licenses.
 *
 * CLI flags:
 *   --strict-3p          fail if a third-party component dir has no license (default: false -> warn)
 *   --no-3p              skip third-party scan
 *   --min-words <n>      minimal word count for root LICENSE (default: 50)
 *   --print-ok           print explicit OK notes (default: false)
 *
 * Exit code:
 *   0 = ok/warnings; 1 = failures.
 */

import fs from 'node:fs';
import path from 'node:path';

const argv = process.argv.slice(2);
const getFlag = (name, def = false) =>
  argv.includes(`--${name}`) ? true : def;
const getNum  = (name, def) => {
  const i = argv.indexOf(`--${name}`);
  if (i >= 0 && i + 1 < argv.length) {
    const v = Number(argv[i + 1]);
    return Number.isFinite(v) ? v : def;
  }
  return def;
};

const STRICT_3P = getFlag('strict-3p', false);
const SKIP_3P   = getFlag('no-3p', false);
const MIN_WORDS = getNum('min-words', 50);
const PRINT_OK  = getFlag('print-ok', false);

const ROOT = process.cwd();
const LICENSE_CANDIDATES = [
  'LICENSE', 'LICENSE.txt', 'LICENSE.md',
  'COPYING', 'COPYING.txt', 'COPYRIGHT', 'COPYRIGHT.txt',
];

const THIRD_PARTY_ROOTS = [
  'third_party', 'third-party', 'vendor',
  'content/third_party', 'content/3p', 'assets/third_party',
];

const failures = [];
const warnings = [];
const notes = [];

function findFirstExistingFile(dir, names) {
  for (const n of names) {
    const p = path.join(dir, n);
    try {
      const st = fs.statSync(p);
      if (st.isFile()) return p;
    } catch {}
  }
  return null;
}

function countWords(txt) {
  return (txt.trim().match(/\S+/g) || []).length;
}

function looksPlaceholder(txt) {
  return /(?:TBD|TODO|INSERT LICENSE|REPLACE ME|PUT LICEN[CS]E HERE)/i.test(txt);
}

function listDirs(dir) {
  try {
    return fs.readdirSync(dir, { withFileTypes: true })
      .filter(d => d.isDirectory()).map(d => path.join(dir, d.name));
  } catch { return []; }
}

function hasAnyLicenseFile(dir) {
  const names = [
    ...LICENSE_CANDIDATES,
    'NOTICE', 'NOTICE.txt', 'LICENSES', 'LICENSES.txt',
    'README', 'README.md',
  ];
  for (const n of names) {
    const p = path.join(dir, n);
    try {
      if (fs.statSync(p).isFile()) return true;
    } catch {}
  }
  return false;
}

// 1) Root license presence & sanity
const licensePath = findFirstExistingFile(ROOT, LICENSE_CANDIDATES);
if (!licensePath) {
  failures.push(
    `[license] Missing repository license file. Expected one of: ${LICENSE_CANDIDATES.join(', ')} in repo root.`
  );
} else {
  const txt = fs.readFileSync(licensePath, 'utf8');
  const words = countWords(txt);
  const licenseRel = path.relative(ROOT, licensePath);
  if (words < MIN_WORDS) {
    failures.push(`[license] Root license too short (${words} words < ${MIN_WORDS}). File: ${licenseRel}`);
  } else if (PRINT_OK) {
    notes.push(`[license] Root license present: ${licenseRel} (${words} words).`);
  }
  if (looksPlaceholder(txt)) {
    failures.push(`[license] Root license contains placeholders (TODO/TBD/etc). File: ${licenseRel}`);
  }
}

// 2) (Optional) Scan third-party/vendor trees
if (!SKIP_3P) {
  for (const rel of THIRD_PARTY_ROOTS) {
    const root = path.join(ROOT, rel);
    if (!fs.existsSync(root)) continue;
    const dirs = listDirs(root);
    for (const d of dirs) {
      if (!hasAnyLicenseFile(d)) {
        const msg = `[license:3p] No license/notice found in ${path.relative(ROOT, d)}`;
        if (STRICT_3P) failures.push(msg); else warnings.push(msg);
      } else if (PRINT_OK) {
        notes.push(`[license:3p] OK: ${path.relative(ROOT, d)}`);
      }
    }
  }
}

// Print summary
const printList = (arr, title) => {
  if (!arr.length) return;
  console.log(`\n${title}`);
  for (const m of arr) console.log(`  - ${m}`);
};

printList(notes,     'Notes');
printList(warnings,  'Warnings');
printList(failures,  'Failures');

if (failures.length) {
  console.error(`\n[license] FAILED with ${failures.length} error(s), ${warnings.length} warning(s).`);
  process.exit(1);
} else {
  console.log(`\n[license] OK (${warnings.length} warning(s))`);
  process.exit(0);
}

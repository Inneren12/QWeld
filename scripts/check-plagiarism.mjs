#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const fsp = fs.promises;

const ROOT_DIR = path.resolve(__dirname, '..');
const CONTENT_DIR = path.join(ROOT_DIR, 'content');
const QUESTIONS_DIR = path.join(CONTENT_DIR, 'questions');
const EXPLANATIONS_DIR = path.join(CONTENT_DIR, 'explanations');

const N_GRAM_SIZE = 5;
const DEFAULT_THRESHOLD = 0.7;
const SIMILARITY_THRESHOLD = Number.parseFloat(process.env.QWELD_PLAGIARISM_THRESHOLD ?? `${DEFAULT_THRESHOLD}`);
const args = parseArgs({
  options: {
    locales: { type: 'string' },
    'shard-index': { type: 'string' },
    'shard-count': { type: 'string' },
    'time-budget': { type: 'string' },
    progress: { type: 'string' },
    out: { type: 'string' },
    'soft-fail': { type: 'boolean' },
    'ignore-cross-locale': { type: 'boolean', default: true },
    changed: { type: 'string', multiple: true },
    'dry-run': { type: 'boolean' },
  },
  allowPositionals: false,
});

const locales = typeof args.values.locales === 'string' && args.values.locales.length > 0
  ? args.values.locales.split(',').map((value) => value.trim()).filter(Boolean)
  : null;
const localeFilter = locales && locales.length > 0 ? new Set(locales) : null;
const shardIndex = args.values['shard-index'] !== undefined ? Number(args.values['shard-index']) : -1;
const shardCount = args.values['shard-count'] !== undefined ? Number(args.values['shard-count']) : -1;
const timeBudgetSec = args.values['time-budget'] !== undefined ? Number(args.values['time-budget']) : 0;
const progressEvery = args.values.progress !== undefined ? Number(args.values.progress) : 0;
const outPath = args.values.out ? String(args.values.out) : null;
const softFail = Boolean(args.values['soft-fail']);
const ignoreCrossLocale = args.values['ignore-cross-locale'] !== false;
const dryRun = Boolean(args.values['dry-run']);
if ((shardIndex >= 0 && shardCount <= 0) || (shardIndex < 0 && shardCount > 0)) {
  throw new Error('Both --shard-index and --shard-count must be provided together.');
}

if (Number.isNaN(shardIndex) || Number.isNaN(shardCount) || Number.isNaN(timeBudgetSec) || Number.isNaN(progressEvery)) {
  throw new Error('Invalid numeric argument provided.');
}

const FULL_SCAN_PATTERNS = [
  /^content\/blueprints\//u,
  /^content\/schema\//u,
  /^schemas\//u,
];

const CONTENT_PATTERNS = [
  /^content\/questions\//u,
  /^content\/explanations\//u,
];

function shouldRunFullScan(paths) {
  for (const rawPath of paths ?? []) {
    const value = typeof rawPath === 'string' ? rawPath.trim() : '';
    if (!value) {
      continue;
    }

    if (FULL_SCAN_PATTERNS.some((pattern) => pattern.test(value))) {
      return true;
    }
  }

  return false;
}

function formatLocaleList(localeSet) {
  if (!localeSet || localeSet.size === 0) {
    return '(all locales)';
  }

  return [...localeSet].sort().join(', ');
}

async function resolveChangedPaths(specs) {
  if (!specs) {
    return [];
  }

  const values = Array.isArray(specs) ? specs : [specs];
  const resolved = [];

  for (const entry of values) {
    if (typeof entry !== 'string' || entry.length === 0) {
      continue;
    }

    if (entry.startsWith('@')) {
      const relative = entry.slice(1).trim();
      if (!relative) {
        continue;
      }

      const absolute = path.isAbsolute(relative) ? relative : path.resolve(ROOT_DIR, relative);
      try {
        const payload = await fsp.readFile(absolute, 'utf8');
        const lines = payload.split(/\r?\n/gu).map((line) => line.trim()).filter(Boolean);
        resolved.push(...lines);
      } catch (error) {
        if (error.code === 'ENOENT') {
          console.warn(`[plagiarism] Changed file list not found: ${relative}`);
          continue;
        }
        throw error;
      }
      continue;
    }

    const parts = entry.split(',');
    for (const part of parts) {
      const trimmed = part.trim();
      if (trimmed) {
        resolved.push(trimmed);
      }
    }
  }

  return resolved;
}

function classifyChangedPaths(paths) {
  const locales = new Set();
  let hasContentChanges = false;
  const requiresFull = shouldRunFullScan(paths);

  for (const rawPath of paths ?? []) {
    const value = rawPath.trim();
    if (!value) {
      continue;
    }

    if (CONTENT_PATTERNS.some((pattern) => pattern.test(value))) {
      hasContentChanges = true;
    }

    const questionMatch = value.match(/^content\/questions\/([a-z]{2})(?:\/|$)/iu);
    if (questionMatch) {
      locales.add(questionMatch[1].toLowerCase());
      continue;
    }

    const explanationMatch = value.match(/^content\/explanations\/([a-z]{2})(?:\/|$)/iu);
    if (explanationMatch) {
      locales.add(explanationMatch[1].toLowerCase());
    }
  }

  return { requiresFull, hasContentChanges, locales };
}

function buildPartialLocaleFilter(partialLocales) {
  if (partialLocales && partialLocales.size > 0) {
    const filter = new Set();
    for (const locale of partialLocales) {
      if (typeof locale === 'string' && locale.trim().length > 0) {
        filter.add(locale.trim());
      }
    }
    return filter;
  }

  if (localeFilter && localeFilter.size > 0) {
    return new Set(localeFilter);
  }

  return null;
}

function decideScanPlan({ changedPaths, changedProvided }) {
  const uniquePaths = Array.from(new Set((changedPaths ?? []).map((value) => value.trim()).filter(Boolean)));
  const { requiresFull, hasContentChanges, locales: partialLocales } = classifyChangedPaths(uniquePaths);

  if (requiresFull) {
    return { type: 'full', reason: 'blueprint/schema change detected', locales: null, changedPaths: uniquePaths };
  }

  if (hasContentChanges) {
    return {
      type: 'partial',
      reason: 'content questions/explanations updated',
      locales: buildPartialLocaleFilter(partialLocales),
      changedPaths: uniquePaths,
    };
  }

  if (!changedProvided) {
    return { type: 'full', reason: 'no changed file list provided', locales: null, changedPaths: uniquePaths };
  }

  if (uniquePaths.length === 0) {
    return { type: 'skip', reason: 'no files changed vs origin/main', locales: null, changedPaths: uniquePaths };
  }

  return { type: 'skip', reason: 'no content files detected', locales: null, changedPaths: uniquePaths };
}

function logPlan(plan, { dryRun: isDryRun }) {
  const prefix = isDryRun ? '[plagiarism][dry-run]' : '[plagiarism]';
  switch (plan.type) {
    case 'full':
      console.log(`${prefix} ${isDryRun ? 'Would run' : 'Running'} full scan (${plan.reason}).`);
      break;
    case 'partial':
      console.log(`${prefix} ${isDryRun ? 'Would run' : 'Running'} partial scan for locales: ${formatLocaleList(plan.locales)}.`);
      break;
    default:
      console.log(`${prefix} ${isDryRun ? 'Would skip' : 'Skipping'} plagiarism scan (${plan.reason}).`);
      break;
  }
}

function normaliseWhitespace(value) {
  return value.replace(/\s+/gu, ' ').trim();
}

function collectTextSegmentsFromQuestion(payload) {
  const segments = [];
  if (typeof payload.stem === 'string') {
    segments.push(payload.stem);
  }

  if (Array.isArray(payload.choices)) {
    for (const choice of payload.choices) {
      if (choice && typeof choice.text === 'string') {
        segments.push(choice.text);
      }
    }
  }

  if (payload.rationales && typeof payload.rationales === 'object') {
    for (const value of Object.values(payload.rationales)) {
      if (typeof value === 'string') {
        segments.push(value);
      }
    }
  }

  if (typeof payload.source === 'string') {
    segments.push(payload.source);
  }

  return segments;
}

function collectTextSegmentsFromExplanation(payload) {
  const segments = [];

  if (typeof payload.summary === 'string') {
    segments.push(payload.summary);
  }

  if (Array.isArray(payload.steps)) {
    for (const step of payload.steps) {
      if (step && typeof step.title === 'string') {
        segments.push(step.title);
      }
      if (step && typeof step.text === 'string') {
        segments.push(step.text);
      }
    }
  }

  if (Array.isArray(payload.why_not)) {
    for (const entry of payload.why_not) {
      if (entry && typeof entry.text === 'string') {
        segments.push(entry.text);
      }
    }
  }

  if (Array.isArray(payload.tips)) {
    for (const tip of payload.tips) {
      if (typeof tip === 'string') {
        segments.push(tip);
      }
    }
  }

  if (Array.isArray(payload.references)) {
    for (const reference of payload.references) {
      if (reference && typeof reference.title === 'string') {
        segments.push(reference.title);
      }
      if (reference && typeof reference.publisher === 'string') {
        segments.push(reference.publisher);
      }
      if (reference && typeof reference.section === 'string') {
        segments.push(reference.section);
      }
    }
  }

  return segments;
}

function collectTextSegments(type, payload) {
  switch (type) {
    case 'question':
      return collectTextSegmentsFromQuestion(payload);
    case 'explanation':
      return collectTextSegmentsFromExplanation(payload);
    default:
      return [];
  }
}

function toTokens(text) {
  const cleaned = normaliseWhitespace(String(text ?? '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' '));

  if (cleaned.length === 0) {
    return [];
  }

  return cleaned.split(' ');
}

function buildFiveGrams(tokens) {
  const grams = new Set();
  if (tokens.length < N_GRAM_SIZE) {
    return grams;
  }

  for (let index = 0; index <= tokens.length - N_GRAM_SIZE; index += 1) {
    const slice = tokens.slice(index, index + N_GRAM_SIZE);
    grams.add(slice.join(' '));
  }

  return grams;
}

function compareDocuments(docA, docB) {
  if (docA.grams.size === 0 || docB.grams.size === 0) {
    return null;
  }

  const [smaller, larger] =
    docA.grams.size <= docB.grams.size ? [docA.grams, docB.grams] : [docB.grams, docA.grams];

  let overlap = 0;
  for (const gram of smaller) {
    if (larger.has(gram)) {
      overlap += 1;
    }
  }

  if (overlap === 0) {
    return null;
  }

  return {
    overlap,
    basis: smaller.size,
    similarity: overlap / smaller.size,
  };
}

async function readJson(filePath) {
  const payload = await fsp.readFile(filePath, 'utf8');
  return JSON.parse(payload);
}

async function collectDocumentsFromDir(dirPath, type, documents) {
  let entries = [];
  try {
    entries = await fsp.readdir(dirPath, { withFileTypes: true });
  } catch (error) {
    if (error.code === 'ENOENT') {
      return;
    }
    throw error;
  }

  for (const entry of entries) {
    const fullPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      await collectDocumentsFromDir(fullPath, type, documents);
      continue;
    }

    if (!entry.isFile() || !entry.name.endsWith('.json')) {
      continue;
    }

    const payload = await readJson(fullPath);
    const relativePath = path.relative(ROOT_DIR, fullPath);
    const id = typeof payload.id === 'string' ? payload.id : entry.name.replace(/\.json$/u, '');
    const locale = typeof payload.locale === 'string' ? payload.locale : inferLocaleFromPath(relativePath);
    const segments = collectTextSegments(type, payload);
    const tokens = segments.flatMap((segment) => toTokens(segment));
    const grams = buildFiveGrams(tokens);

    documents.push({
      id,
      type,
      locale,
      file: relativePath,
      grams,
    });
  }
}

function inferLocaleFromPath(relativePath) {
  const parts = relativePath.split(path.sep);
  const localeIndex = parts.indexOf('questions');
  if (localeIndex >= 0 && parts.length > localeIndex + 1) {
    const candidate = parts[localeIndex + 1];
    if (/^[a-z]{2}$/iu.test(candidate)) {
      return candidate;
    }
  }

  const explanationIndex = parts.indexOf('explanations');
  if (explanationIndex >= 0 && parts.length > explanationIndex + 1) {
    const candidate = parts[explanationIndex + 1];
    if (/^[a-z]{2}$/iu.test(candidate)) {
      return candidate;
    }
  }

  return 'en';
}

async function loadDocuments(filterLocales) {
  const documents = [];
  await collectDocumentsFromDir(QUESTIONS_DIR, 'question', documents);
  await collectDocumentsFromDir(EXPLANATIONS_DIR, 'explanation', documents);
  if (!filterLocales || filterLocales.size === 0) {
    return documents;
  }

  return documents.filter((document) => filterLocales.has(document.locale));
}

function stablePairShard(a, b, count) {
  const key = a.file < b.file ? `${a.file}::${b.file}` : `${b.file}::${a.file}`;
  const hash = crypto.createHash('sha1').update(key).digest().readUInt32BE(0);
  return hash % count;
}

function printResults(matches) {
  if (matches.length === 0) {
    console.log('✅ No potential plagiarism overlaps detected.');
    return;
  }

  console.log('⚠️ Potential plagiarism overlaps detected:');
  for (const match of matches) {
    const similarityPct = (match.similarity * 100).toFixed(1);
    console.log(
      `- ${match.a.file} ↔ ${match.b.file} :: ${similarityPct}% overlap (${match.overlap}/${match.basis} 5-grams)`,
    );
  }
}

async function runPlagiarismScan(filterLocales) {
  const effectiveFilter = filterLocales && filterLocales.size > 0
    ? new Set(filterLocales)
    : (localeFilter && localeFilter.size > 0 ? new Set(localeFilter) : null);

  const documents = await loadDocuments(effectiveFilter);
  if (documents.length === 0) {
    console.error('No documents found to analyse.');
    return {
      documents: 0,
      duplicates: [],
      comparedPairs: 0,
      shardIndex,
      shardCount,
      locales: effectiveFilter ? [...effectiveFilter].sort() : (locales ?? []),
      timeBudgetSec,
      timedOut: false,
      generatedAt: new Date().toISOString(),
    };
  }

  const matches = [];
  let comparisons = 0;
  let timedOut = false;
  const startedAt = Date.now();

  outer: for (let leftIndex = 0; leftIndex < documents.length; leftIndex += 1) {
    const a = documents[leftIndex];
    for (let rightIndex = leftIndex + 1; rightIndex < documents.length; rightIndex += 1) {
      const b = documents[rightIndex];

      if (timeBudgetSec > 0) {
        const elapsedSec = (Date.now() - startedAt) / 1000;
        if (elapsedSec >= timeBudgetSec) {
          if (!timedOut) {
            console.warn('⏱️ Time budget reached; stopping comparisons early.');
          }
          timedOut = true;
          break outer;
        }
      }

      if (shardCount > 0 && shardIndex >= 0) {
        const shard = stablePairShard(a, b, shardCount);
        if (shard !== shardIndex) {
          continue;
        }
      }

      if (ignoreCrossLocale && a.locale !== b.locale) {
        continue;
      }

      comparisons += 1;
      if (progressEvery > 0 && comparisons % progressEvery === 0) {
        console.log(`Progress: compared ${comparisons} pairs.`);
      }

      const comparison = compareDocuments(a, b);
      if (!comparison) {
        continue;
      }

      if (comparison.similarity >= SIMILARITY_THRESHOLD) {
        matches.push({ a, b, ...comparison });
      }
    }
  }

  matches.sort((left, right) => right.similarity - left.similarity);
  printResults(matches);

  return {
    documents: documents.length,
    duplicates: matches,
    comparedPairs: comparisons,
    shardIndex,
    shardCount,
    locales: effectiveFilter ? [...effectiveFilter].sort() : (locales ?? []),
    timeBudgetSec,
    timedOut,
    generatedAt: new Date().toISOString(),
  };
}

async function main() {
  const changedSpecs = args.values.changed;
  const changedProvided = changedSpecs !== undefined;
  const changedPaths = await resolveChangedPaths(changedSpecs);
  const plan = decideScanPlan({ changedPaths, changedProvided });

  if (plan.changedPaths && plan.changedPaths.length > 0) {
    console.log(`[plagiarism] Changed paths (${plan.changedPaths.length}):`);
    for (const filePath of plan.changedPaths) {
      console.log(`[plagiarism]   ${filePath}`);
    }
  } else {
    console.log('[plagiarism] No changed paths detected.');
  }

  logPlan(plan, { dryRun });

  if (dryRun) {
    return null;
  }

  if (plan.type === 'skip') {
    return null;
  }

  if (plan.type === 'partial') {
    const localeSet = plan.locales ? new Set(plan.locales) : null;
    if (!localeSet || localeSet.size === 0) {
      console.log('[plagiarism] No locales resolved for partial run; falling back to full scan.');
      const report = await runPlagiarismScan(null);
      report.mode = 'full';
      report.plan = plan.type;
      report.reason = plan.reason;
      report.changedPaths = plan.changedPaths;
      report.fallback = 'partial-without-locales';
      return report;
    }

    const report = await runPlagiarismScan(localeSet);
    report.mode = 'partial';
    report.plan = plan.type;
    report.reason = plan.reason;
    report.changedPaths = plan.changedPaths;
    return report;
  }

  const report = await runPlagiarismScan(null);
  report.mode = 'full';
  report.plan = plan.type;
  report.reason = plan.reason;
  report.changedPaths = plan.changedPaths;
  return report;
}

let report;
try {
  report = await main();
} catch (error) {
  console.error(error);
  process.exit(1);
}

if (outPath && report) {
  await fsp.writeFile(outPath, JSON.stringify(report, null, 2));
}

if (report?.duplicates?.length && !softFail) {
  process.exit(1);
}

process.exit(0);

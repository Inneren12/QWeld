#!/usr/bin/env node
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';
import { execFile } from 'node:child_process';
import { parseArgs, promisify } from 'node:util';

const execFileAsync = promisify(execFile);

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ROOT_DIR = path.resolve(__dirname, '..');
const CONTENT_DIR = path.join(ROOT_DIR, 'content');
const QUESTIONS_DIR = path.join(CONTENT_DIR, 'questions');
const EXPLANATIONS_DIR = path.join(CONTENT_DIR, 'explanations');

const N_GRAM_SIZE = 5;
const DEFAULT_THRESHOLD = 0.7;
const SIMILARITY_THRESHOLD = Number.parseFloat(process.env.QWELD_PLAGIARISM_THRESHOLD ?? `${DEFAULT_THRESHOLD}`);

const { values: cliArgs } = parseArgs({
  args: process.argv.slice(2),
  options: {
    locales: { type: 'string' },
    'shard-index': { type: 'string' },
    'shard-count': { type: 'string' },
    'time-budget': { type: 'string' },
    progress: { type: 'string' },
    out: { type: 'string' },
  },
});

const shardIndex = cliArgs['shard-index'] !== undefined ? Number(cliArgs['shard-index']) : -1;
const shardCount = cliArgs['shard-count'] !== undefined ? Number(cliArgs['shard-count']) : -1;
const timeBudgetSec = cliArgs['time-budget'] !== undefined ? Number(cliArgs['time-budget']) : 0;
const progressEvery = cliArgs.progress !== undefined ? Number(cliArgs.progress) : 0;
const outPath = cliArgs.out ? String(cliArgs.out) : '';
const startedAt = Date.now();

const localesArg = cliArgs.locales ? String(cliArgs.locales) : '';
const allowedLocales = new Set(
  localesArg
    .split(',')
    .map((value) => value.trim().toLowerCase())
    .filter((value) => value.length > 0),
);

function stablePairShard(a, b, count) {
  if (count <= 0) {
    return 0;
  }

  const key = a.file < b.file ? `${a.file}::${b.file}` : `${b.file}::${a.file}`;
  const hash = crypto.createHash('sha1').update(key).digest().readUInt32BE(0);
  return hash % count;
}

function shouldConsiderLocale(locale) {
  if (allowedLocales.size === 0) {
    return true;
  }
  return allowedLocales.has(String(locale ?? '').toLowerCase());
}

async function shouldRunFullScan() {
  const base = process.env.BASE_SHA ?? process.env.GITHUB_BASE_SHA ?? '';
  const head = process.env.HEAD_SHA ?? process.env.GITHUB_SHA ?? '';

  if (!base || !head || base === head) {
    return true;
  }

  try {
    const { stdout } = await execFileAsync('git', ['diff', '--name-only', `${base}...${head}`]);
    const files = stdout
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0);

    const fullScan = files.some((file) =>
      file.startsWith('content/blueprints/') || file.startsWith('content/exam_profiles/'),
    );

    if (!fullScan) {
      console.log('[plag] no blueprint/profile changes detected; skipping full scan');
    }

    return fullScan;
  } catch (error) {
    console.warn('[plag] unable to determine diff; assuming full scan');
    console.warn(error);
    return true;
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
  const payload = await fs.readFile(filePath, 'utf8');
  return JSON.parse(payload);
}

async function collectDocumentsFromDir(dirPath, type, documents) {
  let entries = [];
  try {
    entries = await fs.readdir(dirPath, { withFileTypes: true });
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

    if (!shouldConsiderLocale(locale)) {
      continue;
    }

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

async function loadDocuments() {
  const documents = [];
  await collectDocumentsFromDir(QUESTIONS_DIR, 'question', documents);
  await collectDocumentsFromDir(EXPLANATIONS_DIR, 'explanation', documents);
  return documents;
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

async function main() {
  if (Number.isNaN(shardIndex) || Number.isNaN(shardCount) || Number.isNaN(timeBudgetSec) || Number.isNaN(progressEvery)) {
    console.error('[plag] invalid numeric CLI arguments');
    process.exitCode = 1;
    return;
  }

  if (shardCount > 0 && (shardIndex < 0 || shardIndex >= shardCount)) {
    console.error(`[plag] shard-index ${shardIndex} is out of bounds for shard-count ${shardCount}`);
    process.exitCode = 1;
    return;
  }

  if (shardCount <= 0 && shardIndex >= 0) {
    console.error('[plag] shard-index provided without shard-count');
    process.exitCode = 1;
    return;
  }

  const requiresFull = await shouldRunFullScan();
  if (!requiresFull) {
    const shardLabel = shardCount > 0 && shardIndex >= 0 ? `${shardIndex}/${shardCount}` : 'all';
    console.log(`[plag] shard ${shardLabel} skipping: full scan not required`);
    return;
  }

  const documents = await loadDocuments();
  if (documents.length === 0) {
    console.error('No documents found to analyse.');
    return;
  }

  const matches = [];
  const totalPairs = (documents.length * (documents.length - 1)) / 2;
  let compared = 0;
  let aborted = false;

  const shardLabel = shardCount > 0 && shardIndex >= 0 ? `${shardIndex}/${shardCount}` : 'all';

  outer: for (let i = 0; i < documents.length; i += 1) {
    for (let j = i + 1; j < documents.length; j += 1) {
      const a = documents[i];
      const b = documents[j];

      if (a.file === b.file) {
        continue;
      }

      if (shardCount > 0 && shardIndex >= 0) {
        const shard = stablePairShard(a, b, shardCount);
        if (shard !== shardIndex) {
          continue;
        }
      }

      if (timeBudgetSec > 0) {
        const elapsed = (Date.now() - startedAt) / 1000;
        if (elapsed > timeBudgetSec) {
          console.log('[plag] time budget reached; producing partial results');
          aborted = true;
          break outer;
        }
      }

      compared += 1;

      if (progressEvery > 0 && compared % progressEvery === 0) {
        console.log(`[plag] progress ${compared}/${totalPairs} pairs (shard=${shardLabel})`);
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

  if (outPath) {
    const report = {
      duplicates: matches,
      meta: {
        compared,
        totalPairs,
        shardIndex: shardIndex >= 0 ? shardIndex : null,
        shardCount: shardCount > 0 ? shardCount : null,
        timeBudgetSec,
        aborted,
        locales: allowedLocales.size > 0 ? Array.from(allowedLocales) : null,
      },
    };
    await fs.writeFile(outPath, `${JSON.stringify(report, null, 2)}\n`);
  }

  if (!aborted && matches.length > 0) {
    process.exitCode = 1;
  }
}

await main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

#!/usr/bin/env node
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ROOT_DIR = path.resolve(__dirname, '..');
const CONTENT_DIR = path.join(ROOT_DIR, 'content');
const QUESTIONS_DIR = path.join(CONTENT_DIR, 'questions');
const EXPLANATIONS_DIR = path.join(CONTENT_DIR, 'explanations');

const N_GRAM_SIZE = 5;
const DEFAULT_THRESHOLD = 0.7;
const SIMILARITY_THRESHOLD = Number.parseFloat(process.env.QWELD_PLAGIARISM_THRESHOLD ?? `${DEFAULT_THRESHOLD}`);

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
  const documents = await loadDocuments();
  if (documents.length === 0) {
    console.error('No documents found to analyse.');
    return;
  }

  const matches = [];
  const seenPairs = new Set();

  for (const a of documents) {
    for (const b of documents) {
      if (a === b) {
        continue;
      }

      // same file — нет смысла сравнивать
      if (a.file === b.file) {
        continue;
      }

      const key = `${a.file}::${b.file}`;
      const reverse = `${b.file}::${a.file}`;
      // сравнение разных файлов даже при одинаковом id/type — разрешено
      // защита от повторов пары
      if (seenPairs.has(key) || seenPairs.has(reverse)) {
        continue;
      }
      seenPairs.add(key);

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

  if (matches.length > 0) {
    process.exitCode = 1;
  }
}

await main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

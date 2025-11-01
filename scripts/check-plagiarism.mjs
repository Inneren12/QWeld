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
const LOG_DIR = path.join(ROOT_DIR, 'logs');

const FIVE_GRAM_SIZE = 5;
const LONG_QUOTE_GRAM_SIZE = 25;
const DEFAULT_THRESHOLD = 0.85;

const SIMILARITY_THRESHOLD = Number.parseFloat(process.env.PLAG_THRESH ?? `${DEFAULT_THRESHOLD}`);

if (Number.isNaN(SIMILARITY_THRESHOLD)) {
  console.error('Invalid PLAG_THRESH value supplied. Expecting a number.');
  process.exit(1);
}

const normalizeWhitespace = (value) => value.replace(/\s+/gu, ' ').trim();

function collectQuestionSegments(payload) {
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

function collectExplanationSegments(payload) {
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

  if (Array.isArray(payload.media)) {
    for (const media of payload.media) {
      if (media && typeof media.caption === 'string') {
        segments.push(media.caption);
      }
    }
  }

  return segments;
}

function collectSegments(type, payload) {
  switch (type) {
    case 'question':
      return collectQuestionSegments(payload);
    case 'explanation':
      return collectExplanationSegments(payload);
    default:
      return [];
  }
}

function toTokens(text) {
  const cleaned = normalizeWhitespace(String(text ?? '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' '));

  if (cleaned.length === 0) {
    return [];
  }

  return cleaned.split(' ');
}

function buildNGrams(tokens, size) {
  const grams = new Set();

  if (tokens.length < size) {
    return grams;
  }

  for (let index = 0; index <= tokens.length - size; index += 1) {
    const slice = tokens.slice(index, index + size);
    grams.add(slice.join(' '));
  }

  return grams;
}

async function readJson(filePath) {
  const payload = await fs.readFile(filePath, 'utf8');
  return JSON.parse(payload);
}

function inferLocaleFromPath(relativePath) {
  const parts = relativePath.split(path.sep);
  for (const directory of ['questions', 'explanations']) {
    const index = parts.indexOf(directory);
    if (index >= 0 && parts.length > index + 1) {
      const candidate = parts[index + 1];
      if (/^[a-z]{2}$/iu.test(candidate)) {
        return candidate.toLowerCase();
      }
    }
  }

  return 'en';
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

    let payload;
    try {
      payload = await readJson(fullPath);
    } catch (error) {
      console.error(`[plag_error] file=${path.relative(ROOT_DIR, fullPath)} message="${error.message}"`);
      continue;
    }

    const relativePath = path.relative(ROOT_DIR, fullPath);
    const locale = typeof payload.locale === 'string'
      ? payload.locale.toLowerCase()
      : inferLocaleFromPath(relativePath);
    const id = typeof payload.id === 'string'
      ? payload.id
      : entry.name.replace(/\.json$/u, '');

    const segments = collectSegments(type, payload);
    const tokens = segments.flatMap((segment) => toTokens(segment));

    documents.push({
      id,
      type,
      locale,
      file: relativePath,
      tokens,
      fiveGrams: buildNGrams(tokens, FIVE_GRAM_SIZE),
      longQuoteGrams: buildNGrams(tokens, LONG_QUOTE_GRAM_SIZE),
    });
  }
}

async function loadDocuments() {
  const documents = [];
  await collectDocumentsFromDir(QUESTIONS_DIR, 'question', documents);
  await collectDocumentsFromDir(EXPLANATIONS_DIR, 'explanation', documents);
  return documents;
}

function computeJaccard(gramsA, gramsB) {
  if (gramsA.size === 0 && gramsB.size === 0) {
    return null;
  }

  const [smaller, larger] = gramsA.size <= gramsB.size ? [gramsA, gramsB] : [gramsB, gramsA];
  let intersectionCount = 0;

  for (const gram of smaller) {
    if (larger.has(gram)) {
      intersectionCount += 1;
    }
  }

  if (intersectionCount === 0) {
    return {
      similarity: 0,
      intersectionCount,
      unionCount: gramsA.size + gramsB.size,
    };
  }

  const unionCount = gramsA.size + gramsB.size - intersectionCount;

  if (unionCount === 0) {
    return null;
  }

  return {
    similarity: intersectionCount / unionCount,
    intersectionCount,
    unionCount,
  };
}

function detectLongQuotes(docA, docB) {
  if (docA.longQuoteGrams.size === 0 || docB.longQuoteGrams.size === 0) {
    return [];
  }

  const [smaller, larger] =
    docA.longQuoteGrams.size <= docB.longQuoteGrams.size
      ? [docA.longQuoteGrams, docB.longQuoteGrams]
      : [docB.longQuoteGrams, docA.longQuoteGrams];

  const matches = [];
  for (const gram of smaller) {
    if (larger.has(gram)) {
      matches.push(gram);
      if (matches.length >= 5) {
        break;
      }
    }
  }

  return matches;
}

function formatLongQuoteSnippet(gram) {
  const tokens = gram.split(' ');
  const snippetTokens = tokens.slice(0, 12);
  return `${snippetTokens.join(' ')}${tokens.length > snippetTokens.length ? '…' : ''}`;
}

async function ensureLogDir() {
  await fs.mkdir(LOG_DIR, { recursive: true });
}

async function writeReportMarkdown(reportPath, summary) {
  await fs.writeFile(reportPath, `${summary}\n`, 'utf8');
}

async function writeReportCsv(reportPath, rows) {
  await fs.writeFile(reportPath, `${rows}\n`, 'utf8');
}

function buildReports(results, filesCount, pairsChecked, maxSimilarity) {
  const headerLines = [
    '# Plagiarism Report',
    '',
    `- Files analysed: ${filesCount}`,
    `- Pairs checked (same locale only): ${pairsChecked}`,
    `- Threshold (Jaccard over 5-grams): ${SIMILARITY_THRESHOLD.toFixed(2)}`,
    `- Maximum similarity observed: ${maxSimilarity.toFixed(4)}`,
    '',
  ];

  let markdown = headerLines.join('\n');

  if (results.length === 0) {
    markdown += '✅ No high-similarity overlaps detected.\n';
  } else {
    markdown += '| Locale | File A | File B | Similarity | Long quote matches |\n';
    markdown += '| --- | --- | --- | --- | --- |\n';
    for (const result of results) {
      const similarityPct = (result.similarity * 100).toFixed(2);
      const longQuoteCell = result.longQuoteMatches.length === 0
        ? '—'
        : result.longQuoteMatches.map((match) => `“${formatLongQuoteSnippet(match)}”`).join('<br>');
      markdown += `| ${result.locale} | ${result.a.file} | ${result.b.file} | ${similarityPct}% | ${longQuoteCell} |\n`;
    }
  }

  const csvLines = ['locale,file_a,file_b,similarity,intersection_5gram,union_5gram,long_quote_count,long_quote_example'];
  for (const result of results) {
    const escapedSnippet = result.longQuoteMatches.length === 0
      ? ''
      : `"${formatLongQuoteSnippet(result.longQuoteMatches[0]).replace(/"/gu, '""')}"`;
    csvLines.push([
      result.locale,
      `"${result.a.file.replace(/"/gu, '""')}"`,
      `"${result.b.file.replace(/"/gu, '""')}"`,
      result.similarity.toFixed(6),
      result.intersectionCount,
      result.unionCount,
      result.longQuoteMatches.length,
      escapedSnippet,
    ].join(','));
  }

  return {
    markdown,
    csv: csvLines.join('\n'),
  };
}

async function main() {
  const documents = await loadDocuments();

  if (documents.length === 0) {
    console.warn('[plag] No documents found to analyse.');
    return;
  }

  let pairsChecked = 0;
  let maxSimilarity = 0;
  const failingResults = [];
  const analysedPairs = [];

  const documentsByLocale = documents.reduce((acc, doc) => {
    if (!acc.has(doc.locale)) {
      acc.set(doc.locale, []);
    }
    acc.get(doc.locale).push(doc);
    return acc;
  }, new Map());

  for (const [locale, docs] of documentsByLocale.entries()) {
    for (let index = 0; index < docs.length; index += 1) {
      for (let inner = index + 1; inner < docs.length; inner += 1) {
        const a = docs[index];
        const b = docs[inner];

        if (a.id === b.id) {
          continue;
        }

        pairsChecked += 1;

        const jaccard = computeJaccard(a.fiveGrams, b.fiveGrams);

        if (!jaccard) {
          continue;
        }

        const longQuoteMatches = detectLongQuotes(a, b);
        const similarity = jaccard.similarity;
        maxSimilarity = Math.max(maxSimilarity, similarity);

        const result = {
          locale,
          a,
          b,
          similarity,
          intersectionCount: jaccard.intersectionCount,
          unionCount: jaccard.unionCount,
          longQuoteMatches,
        };

        analysedPairs.push(result);

        const failsThreshold = similarity >= SIMILARITY_THRESHOLD;
        const failsQuote = longQuoteMatches.length > 0;

        if (failsThreshold || failsQuote) {
          failingResults.push(result);

          const similarityStr = similarity.toFixed(4);
          if (failsThreshold) {
            console.log(
              `[plag_fail] fileA=${a.file} fileB=${b.file} locale=${locale} sim=${similarityStr} reason=threshold`,
            );
          }

          if (failsQuote) {
            console.log(
              `[plag_fail] fileA=${a.file} fileB=${b.file} locale=${locale} longQuoteCount=${longQuoteMatches.length} reason=long_quote`,
            );
          }
        }
      }
    }
  }

  const sortedResults = analysedPairs
    .filter((item) => item.similarity > 0 || item.longQuoteMatches.length > 0)
    .sort((left, right) => right.similarity - left.similarity)
    .slice(0, 50);

  console.log(
    `[plag] files=${documents.length} pairsChecked=${pairsChecked} maxSim=${maxSimilarity.toFixed(4)}`,
  );

  await ensureLogDir();
  const reports = buildReports(sortedResults, documents.length, pairsChecked, maxSimilarity);
  await writeReportMarkdown(path.join(LOG_DIR, 'plagiarism-report.md'), reports.markdown);
  await writeReportCsv(path.join(LOG_DIR, 'plagiarism-report.csv'), reports.csv);

  if (failingResults.length > 0) {
    process.exitCode = 1;
  }
}

await main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

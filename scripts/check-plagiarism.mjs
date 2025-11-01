#!/usr/bin/env node
import fs from 'fs/promises';
import path from 'path';
import process from 'process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');
const QUESTION_DIR = path.join(ROOT, 'content', 'questions');
const EXPLANATION_DIR = path.join(ROOT, 'content', 'explanations');
const LOG_DIR = path.join(ROOT, 'logs');
const REPORT_PATH = path.join(LOG_DIR, 'plagiarism-report.md');
const THRESHOLD = Number.parseFloat(process.env.PLAG_THRESH || '0.85');
const SHINGLE_SIZE = 5;
const LONG_QUOTE_LIMIT = 25;
const PROGRESS_LOG_INTERVAL = 25000;
const skipSameIdFlag = process.argv.includes('--skip-same-id');

const WORD_REGEX = /[\p{L}\p{N}][\p{L}\p{N}'’\-]*/gu;
const QUOTE_PATTERNS = [/"([^"\\]*(?:\\.[^"\\]*)*)"/g, /“([^”]+)”/g, /«([^»]+)»/g];

async function pathExists(p) {
  try {
    await fs.access(p);
    return true;
  } catch (err) {
    return false;
  }
}

async function walkJsonFiles(dir) {
  if (!(await pathExists(dir))) {
    return [];
  }

  const stack = [dir];
  const files = [];
  while (stack.length > 0) {
    const current = stack.pop();
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.name.startsWith('.')) {
        continue;
      }
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (entry.isFile() && entry.name.endsWith('.json')) {
        files.push(fullPath);
      }
    }
  }
  return files;
}

function tokenize(text) {
  if (!text || typeof text !== 'string') {
    return [];
  }
  return text
    .toLowerCase()
    .match(WORD_REGEX)
    ?.filter(Boolean) ?? [];
}

function makeShingles(tokens, size) {
  const result = new Set();
  if (tokens.length === 0) {
    return result;
  }
  if (tokens.length <= size) {
    result.add(tokens.join(' '));
    return result;
  }
  for (let i = 0; i <= tokens.length - size; i += 1) {
    result.add(tokens.slice(i, i + size).join(' '));
  }
  return result;
}

function detectRepeatedWordRuns(tokens, limit) {
  const runs = [];
  if (tokens.length === 0) {
    return runs;
  }
  let runWord = tokens[0];
  let runLength = 1;
  for (let i = 1; i < tokens.length; i += 1) {
    const token = tokens[i];
    if (token === runWord) {
      runLength += 1;
    } else {
      if (runLength >= limit) {
        runs.push({ word: runWord, length: runLength, endIndex: i - 1 });
      }
      runWord = token;
      runLength = 1;
    }
  }
  if (runLength >= limit) {
    runs.push({ word: runWord, length: runLength, endIndex: tokens.length - 1 });
  }
  return runs;
}

function extractQuotedSegments(text) {
  const segments = [];
  if (!text) {
    return segments;
  }
  for (const pattern of QUOTE_PATTERNS) {
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(text)) !== null) {
      const quoteText = match[1];
      if (quoteText) {
        segments.push(quoteText);
      }
    }
  }
  return segments;
}

function detectLongQuoteViolations(text, tokens) {
  const violations = [];
  const quotedSegments = extractQuotedSegments(text);
  for (const segment of quotedSegments) {
    const segmentTokens = tokenize(segment);
    if (segmentTokens.length >= LONG_QUOTE_LIMIT) {
      violations.push({
        type: 'quoted_segment',
        length: segmentTokens.length,
        excerpt: segmentTokens.slice(0, LONG_QUOTE_LIMIT).join(' '),
      });
    }
  }

  const repeatedRuns = detectRepeatedWordRuns(tokens, LONG_QUOTE_LIMIT);
  for (const run of repeatedRuns) {
    violations.push({
      type: 'repeated_word',
      length: run.length,
      word: run.word,
    });
  }

  return violations;
}

function relPath(p) {
  return path.relative(ROOT, p) || path.basename(p);
}

function deriveLocaleFromPath(baseDir, filePath) {
  const rel = path.relative(baseDir, filePath);
  const parts = rel.split(path.sep).filter(Boolean);
  if (parts.length === 0) {
    return 'default';
  }
  const maybeLocale = parts[0];
  if (/^[a-z]{2}(?:-[A-Za-z0-9]+)?$/.test(maybeLocale)) {
    return maybeLocale;
  }
  return 'default';
}

function buildEntry({
  id,
  locale,
  type,
  file,
  segments,
}) {
  const allTokens = segments.flatMap((segment) => segment.tokens);
  const shingles = makeShingles(allTokens, SHINGLE_SIZE);
  const longShingles = makeShingles(allTokens, LONG_QUOTE_LIMIT);
  return {
    id,
    locale,
    type,
    file,
    segments,
    tokens: allTokens,
    shingles,
    longShingles,
  };
}

async function collectQuestions() {
  const files = await walkJsonFiles(QUESTION_DIR);
  const entries = [];
  for (const file of files) {
    const content = await fs.readFile(file, 'utf8');
    let data;
    try {
      data = JSON.parse(content);
    } catch (err) {
      console.error(`[plag_fail] file=${relPath(file)} reason=json_parse error=${err.message}`);
      continue;
    }
    const segments = [];
    const pushSegment = (field, text) => {
      const tokens = tokenize(text);
      const violations = detectLongQuoteViolations(text, tokens);
      if (violations.length > 0) {
        longQuoteFailures.push({
          file,
          field,
          violations,
        });
      }
      segments.push({ field, text, tokens });
    };
    if (typeof data.stem === 'string') {
      pushSegment('stem', data.stem);
    }
    if (Array.isArray(data.choices)) {
      for (const choice of data.choices) {
        if (choice && typeof choice.text === 'string') {
          pushSegment(`choice:${choice.id ?? 'unknown'}`, choice.text);
        }
      }
    }
    if (data.rationales && typeof data.rationales === 'object') {
      for (const [choiceId, rationale] of Object.entries(data.rationales)) {
        if (typeof rationale === 'string') {
          pushSegment(`rationale:${choiceId}`, rationale);
        }
      }
    }
    const locale = deriveLocaleFromPath(QUESTION_DIR, file);
    const id = typeof data.id === 'string' ? data.id : relPath(file);
    entries.push(buildEntry({
      id,
      locale,
      type: 'question',
      file,
      segments,
    }));
  }
  return entries;
}

async function collectExplanations() {
  const files = await walkJsonFiles(EXPLANATION_DIR);
  const entries = [];
  for (const file of files) {
    const content = await fs.readFile(file, 'utf8');
    let data;
    try {
      data = JSON.parse(content);
    } catch (err) {
      console.error(`[plag_fail] file=${relPath(file)} reason=json_parse error=${err.message}`);
      continue;
    }
    const segments = [];
    const pushSegment = (field, text) => {
      const tokens = tokenize(text);
      const violations = detectLongQuoteViolations(text, tokens);
      if (violations.length > 0) {
        longQuoteFailures.push({
          file,
          field,
          violations,
        });
      }
      segments.push({ field, text, tokens });
    };
    if (typeof data.summary === 'string') {
      pushSegment('summary', data.summary);
    }
    if (Array.isArray(data.steps)) {
      data.steps.forEach((step, index) => {
        if (step && typeof step.title === 'string') {
          pushSegment(`step:${index}:title`, step.title);
        }
        if (step && typeof step.text === 'string') {
          pushSegment(`step:${index}:text`, step.text);
        }
      });
    }
    if (Array.isArray(data.why_not)) {
      data.why_not.forEach((item, index) => {
        if (item && typeof item.text === 'string') {
          pushSegment(`why_not:${item.choiceId ?? index}`, item.text);
        }
      });
    }
    if (Array.isArray(data.tips)) {
      data.tips.forEach((tip, index) => {
        if (typeof tip === 'string') {
          pushSegment(`tip:${index}`, tip);
        }
      });
    }
    const locale = typeof data.locale === 'string' ? data.locale : deriveLocaleFromPath(EXPLANATION_DIR, file);
    const id = typeof data.id === 'string' ? data.id : relPath(file);
    entries.push(buildEntry({
      id,
      locale,
      type: 'explanation',
      file,
      segments,
    }));
  }
  return entries;
}

function jaccardSimilarity(aSet, bSet) {
  if (aSet.size === 0 && bSet.size === 0) {
    return 0;
  }
  const [smaller, larger] = aSet.size <= bSet.size ? [aSet, bSet] : [bSet, aSet];
  let intersection = 0;
  for (const token of smaller) {
    if (larger.has(token)) {
      intersection += 1;
    }
  }
  if (intersection === 0) {
    if (larger.size === 0) {
      return 0;
    }
  }
  const union = aSet.size + bSet.size - intersection;
  return union === 0 ? 0 : intersection / union;
}

function summarizeViolations(violations) {
  return violations
    .map((violation) => {
      if (violation.type === 'quoted_segment') {
        return `quoted_segment(${violation.length} words): "${violation.excerpt}…"`;
      }
      if (violation.type === 'repeated_word') {
        return `repeated_word(${violation.length}× "${violation.word}")`;
      }
      return violation.type;
    })
    .join('; ');
}

const longQuoteFailures = [];

export function shouldCompareEntries(a, b, options, seenPairs) {
  if (!a || !b) {
    return false;
  }

  if (a.file === b.file) {
    return false;
  }

  if (options?.skipSameId && a.id === b.id && a.type === b.type) {
    return false;
  }

  const first = a.file <= b.file ? a.file : b.file;
  const second = a.file <= b.file ? b.file : a.file;
  const pairKey = `${first}::${second}`;
  if (seenPairs.has(pairKey)) {
    return false;
  }
  seenPairs.add(pairKey);
  return true;
}

async function main() {
  const entries = [...await collectQuestions(), ...await collectExplanations()];
  const localeMap = new Map();
  for (const entry of entries) {
    if (!entry || entry.tokens.length === 0) {
      continue;
    }
    const list = localeMap.get(entry.locale) ?? [];
    list.push(entry);
    localeMap.set(entry.locale, list);
  }

  const options = { skipSameId: skipSameIdFlag };

  let pairsChecked = 0;
  let maxSim = 0;
  const pairResults = [];
  const failingPairs = [];
  const longQuotePairFailures = [];

  const totalPairs = Array.from(localeMap.values()).reduce((total, localeEntries) => {
    const seen = new Set();
    let count = 0;
    for (let i = 0; i < localeEntries.length; i += 1) {
      const a = localeEntries[i];
      for (let j = i + 1; j < localeEntries.length; j += 1) {
        const b = localeEntries[j];
        if (shouldCompareEntries(a, b, options, seen)) {
          count += 1;
        }
      }
    }
    return total + count;
  }, 0);

  let comparisonsDone = 0;

  for (const [locale, localeEntries] of localeMap.entries()) {
    const seenPairs = new Set();
    for (let i = 0; i < localeEntries.length; i += 1) {
      const a = localeEntries[i];
      for (let j = i + 1; j < localeEntries.length; j += 1) {
        const b = localeEntries[j];
        if (!shouldCompareEntries(a, b, options, seenPairs)) {
          continue;
        }
        comparisonsDone += 1;
        if (totalPairs > 0 && (comparisonsDone % PROGRESS_LOG_INTERVAL) === 0) {
          console.log(`[plag] progress ${comparisonsDone}/${totalPairs} pairs`);
        }
        pairsChecked += 1;
        const sim = jaccardSimilarity(a.shingles, b.shingles);
        if (sim > maxSim) {
          maxSim = sim;
        }
        const commonLong = [];
        if (a.longShingles.size > 0 && b.longShingles.size > 0) {
          for (const shingle of a.longShingles) {
            if (b.longShingles.has(shingle)) {
              commonLong.push(shingle);
              if (commonLong.length >= 3) {
                break;
              }
            }
          }
        }
        if (sim >= 0.1 || commonLong.length > 0) {
          pairResults.push({ locale, a, b, sim, commonLong });
        }
        if (sim >= THRESHOLD) {
          failingPairs.push({ locale, a, b, sim });
          console.error(`[plag_fail] fileA=${relPath(a.file)} fileB=${relPath(b.file)} locale=${locale} sim=${sim.toFixed(4)}`);
        }
        if (commonLong.length > 0) {
          longQuotePairFailures.push({ locale, a, b, excerpts: commonLong });
          console.error(`[plag_fail] fileA=${relPath(a.file)} fileB=${relPath(b.file)} locale=${locale} reason=long_quote_25`);
        }
      }
    }
  }

  if (totalPairs > 0 && (comparisonsDone % PROGRESS_LOG_INTERVAL) !== 0) {
    console.log(`[plag] progress ${comparisonsDone}/${totalPairs} pairs`);
  }

  for (const failure of longQuoteFailures) {
    const reason = summarizeViolations(failure.violations);
    console.error(`[plag_fail] file=${relPath(failure.file)} field=${failure.field} reason=${reason}`);
  }

  const summaryLine = `[plag] files=${entries.length} pairsChecked=${pairsChecked} maxSim=${maxSim.toFixed(4)} threshold=${THRESHOLD.toFixed(2)}`;
  console.log(summaryLine);

  await fs.mkdir(LOG_DIR, { recursive: true });

  const lines = [];
  lines.push('# Plagiarism Report');
  lines.push('');
  lines.push(`- Files scanned: ${entries.length}`);
  lines.push(`- Pairs compared: ${pairsChecked}`);
  lines.push(`- Threshold: ${THRESHOLD.toFixed(2)}`);
  lines.push(`- Maximum similarity: ${maxSim.toFixed(4)}`);
  lines.push('');

  if (failingPairs.length > 0) {
    lines.push('## ❌ Blocking similarity matches');
    lines.push('');
    lines.push('| Locale | File A | Type A | File B | Type B | Similarity |');
    lines.push('| --- | --- | --- | --- | --- | --- |');
    for (const { locale, a, b, sim } of failingPairs.sort((x, y) => y.sim - x.sim)) {
      lines.push(`| ${locale} | ${relPath(a.file)} | ${a.type} | ${relPath(b.file)} | ${b.type} | ${sim.toFixed(4)} |`);
    }
    lines.push('');
  }

  const sortedPairs = pairResults
    .filter((item) => item.sim >= 0.4 || item.commonLong.length > 0)
    .sort((a, b) => b.sim - a.sim)
    .slice(0, 50);
  if (sortedPairs.length > 0) {
    lines.push('## ⚠️ High-similarity pairs (top 50)');
    lines.push('');
    lines.push('| Locale | File A | File B | Similarity | Long quote matches |');
    lines.push('| --- | --- | --- | --- | --- |');
    for (const { locale, a, b, sim, commonLong } of sortedPairs) {
      const excerpt = commonLong.length > 0 ? `${commonLong[0].split(' ').slice(0, LONG_QUOTE_LIMIT).join(' ')}…` : '';
      lines.push(`| ${locale} | ${relPath(a.file)} | ${relPath(b.file)} | ${sim.toFixed(4)} | ${commonLong.length > 0 ? `${commonLong.length} × 25-gram (${excerpt})` : ''} |`);
    }
    lines.push('');
  }

  if (longQuoteFailures.length > 0) {
    lines.push('## ❌ Long quote violations (per document)');
    lines.push('');
    lines.push('| File | Field | Details |');
    lines.push('| --- | --- | --- |');
    for (const failure of longQuoteFailures) {
      lines.push(`| ${relPath(failure.file)} | ${failure.field} | ${summarizeViolations(failure.violations)} |`);
    }
    lines.push('');
  }

  if (longQuotePairFailures.length > 0) {
    lines.push('## ❌ Cross-document 25-word matches');
    lines.push('');
    lines.push('| Locale | File A | File B | Samples |');
    lines.push('| --- | --- | --- | --- |');
    for (const match of longQuotePairFailures) {
      const sample = match.excerpts[0]?.split(' ').slice(0, LONG_QUOTE_LIMIT).join(' ');
      lines.push(`| ${match.locale} | ${relPath(match.a.file)} | ${relPath(match.b.file)} | ${sample ? sample + '…' : ''} |`);
    }
    lines.push('');
  }

  const csvLines = ['locale,file_a,type_a,file_b,type_b,similarity'];
  for (const { locale, a, b, sim } of pairResults.sort((x, y) => y.sim - x.sim)) {
    csvLines.push(`${JSON.stringify(locale)},${JSON.stringify(relPath(a.file))},${JSON.stringify(a.type)},${JSON.stringify(relPath(b.file))},${JSON.stringify(b.type)},${sim.toFixed(4)}`);
  }

  lines.push('## CSV snapshot');
  lines.push('');
  lines.push('```csv');
  lines.push(...csvLines);
  lines.push('```');
  lines.push('');

  await fs.writeFile(REPORT_PATH, lines.join('\n'), 'utf8');

  const hasBlocking = failingPairs.length > 0 || longQuoteFailures.length > 0 || longQuotePairFailures.length > 0;
  if (hasBlocking) {
    process.exitCode = 1;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === __filename) {
  main().catch((err) => {
    console.error('[plag_fail] fatal', err);
    process.exitCode = 1;
  });
}

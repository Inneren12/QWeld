import { promises as fs } from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ROOT_DIR = path.resolve(__dirname, '..');
const CONTENT_ROOT = path.join(ROOT_DIR, 'content', 'questions');
const DIST_ROOT = path.join(ROOT_DIR, 'dist', 'questions');
const LOCALES = ['en'];
const STRICT_MODE = process.argv.includes('--strict');

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function readJson(filePath) {
  const payload = await fs.readFile(filePath, 'utf8');
  return JSON.parse(payload);
}

function validateQuestion(question, { locale, taskId, filePath, itemIndex }) {
  const issues = [];
  if (!question || typeof question !== 'object') {
    issues.push('Question is not an object');
  } else {
    if (typeof question.id !== 'string' || question.id.trim().length === 0) {
      issues.push('Missing or empty id');
    }
    if (typeof question.taskId !== 'string' || question.taskId.trim().length === 0) {
      issues.push('Missing or empty taskId');
    }
    if (question.difficulty === undefined || question.difficulty === null) {
      issues.push('Missing difficulty');
    }

    if (!Array.isArray(question.choices) || question.choices.length === 0) {
      issues.push('Missing choices array');
    } else {
      question.choices.forEach((choice, index) => {
        if (typeof choice !== 'object') {
          issues.push(`Choice[${index}] is not an object`);
          return;
        }
        if (typeof choice.id !== 'string' || choice.id.trim().length === 0) {
          issues.push(`Choice[${index}] missing id`);
        }
        if (typeof choice.text !== 'string' || choice.text.trim().length === 0) {
          issues.push(`Choice[${index}] missing text`);
        }
      });
    }

    if (typeof question.correctId !== 'string' || question.correctId.trim().length === 0) {
      issues.push('Missing correctId');
    }
  }

  if (issues.length > 0) {
    const indexLabel = itemIndex !== undefined ? ` index=${itemIndex}` : '';
    const prefix = `[dist-node] ${STRICT_MODE ? 'ERROR' : 'WARN'} malformed_question locale=${locale} task=${taskId} file=${path.relative(ROOT_DIR, filePath)}${indexLabel}`;
    const details = `${prefix} issues=${issues.join('; ')} data=${JSON.stringify(question)}`;
    if (STRICT_MODE) {
      throw new Error(details);
    } else {
      console.warn(details);
    }
  }

  return issues;
}

function normalizeSortKey(question) {
  if (!question || typeof question !== 'object') {
    return '';
  }

  const candidates = [question.id, question.taskId];
  for (const candidate of candidates) {
    if (typeof candidate === 'string' && candidate.trim().length > 0) {
      return candidate;
    }
  }

  return '';
}

function sortById(a, b) {
  const keyA = normalizeSortKey(a);
  const keyB = normalizeSortKey(b);
  return keyA.localeCompare(keyB, 'en', { numeric: true });
}

async function collectTaskQuestions(localeDir, taskId) {
  const taskDir = path.join(localeDir, taskId);
  const entries = await fs.readdir(taskDir, { withFileTypes: true });
  const questions = [];

  for (const entry of entries) {
    if (!entry.isFile() || !entry.name.endsWith('.json')) {
      continue;
    }
    const filePath = path.join(taskDir, entry.name);
    const payload = await readJson(filePath);
    const questionList = Array.isArray(payload) ? payload : [payload];

    questionList.forEach((question, index) => {
      const issues = validateQuestion(question, { locale: path.basename(localeDir), taskId, filePath, itemIndex: index });
      if (!issues.length) {
        questions.push(question);
      }
    });
  }

  questions.sort(sortById);
  return questions;
}

function hashPayload(payload) {
  return crypto.createHash('sha256').update(payload, 'utf8').digest('hex');
}

async function buildForLocale(locale) {
  const localeDir = path.join(CONTENT_ROOT, locale);
  const distLocaleDir = path.join(DIST_ROOT, locale);
  const distTasksDir = path.join(distLocaleDir, 'tasks');

  await ensureDir(distTasksDir);

  const entries = await fs.readdir(localeDir, { withFileTypes: true });
  const taskDirs = entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort((a, b) => a.localeCompare(b));

  const allQuestions = [];

  const taskCounts = {};
  const taskHashes = {};

  for (const taskId of taskDirs) {
    const taskQuestions = await collectTaskQuestions(localeDir, taskId);
    allQuestions.push(...taskQuestions);

    const taskFilePath = path.join(distTasksDir, `${taskId}.json`);
    const taskPayload = `${JSON.stringify(taskQuestions, null, 2)}\n`;
    await fs.writeFile(taskFilePath, taskPayload, 'utf8');

    taskCounts[taskId] = taskQuestions.length;
    taskHashes[taskId] = hashPayload(taskPayload);
  }

  allQuestions.sort(sortById);
  const bankPayload = `${JSON.stringify(allQuestions, null, 2)}\n`;
  const bankPath = path.join(distLocaleDir, 'bank.v1.json');
  await fs.writeFile(bankPath, bankPayload, 'utf8');

  console.log(`[dist-node] ${locale} tasks=${taskDirs.length} total=${allQuestions.length}`);

  return {
    total: allQuestions.length,
    tasks: taskCounts,
    sha256: {
      bank: hashPayload(bankPayload),
      tasks: taskHashes,
    },
  };
}

async function main() {
  await ensureDir(DIST_ROOT);

  const indexLocales = {};
  for (const locale of LOCALES) {
    indexLocales[locale] = await buildForLocale(locale);
  }

  const indexPayload = {
    schema: 'qweld.dist.index.v1',
    locales: indexLocales,
    generatedAt: new Date().toISOString(),
  };

  const indexPath = path.join(DIST_ROOT, 'index.json');
  const indexJson = `${JSON.stringify(indexPayload, null, 2)}\n`;
  await fs.writeFile(indexPath, indexJson, 'utf8');

  console.log(`[dist_index] written=${path.relative(ROOT_DIR, indexPath)}`);
}

main().catch((error) => {
  console.error('[dist-node] ERROR', error);
  process.exit(1);
});

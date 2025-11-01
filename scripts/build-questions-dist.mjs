import { promises as fs } from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ROOT_DIR = path.resolve(__dirname, '..');
const CONTENT_ROOT = path.join(ROOT_DIR, 'content', 'questions');
const DIST_ROOT = path.join(ROOT_DIR, 'dist', 'questions');
const LOCALES = ['en', 'ru'];

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function readJson(filePath) {
  const payload = await fs.readFile(filePath, 'utf8');
  return JSON.parse(payload);
}

function sortById(a, b) {
  return a.id.localeCompare(b.id);
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
    const question = await readJson(filePath);
    questions.push(question);
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

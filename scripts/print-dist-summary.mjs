import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ROOT_DIR = path.resolve(__dirname, '..');
const DIST_ROOT = path.join(ROOT_DIR, 'dist', 'questions');
const EXPECTED_TASKS = [
  'A-1',
  'A-2',
  'A-3',
  'A-4',
  'A-5',
  'B-6',
  'B-7',
  'C-8',
  'C-9',
  'C-10',
  'C-11',
  'D-12',
  'D-13',
  'D-14',
  'D-15',
];

function compareTaskIds(a, b) {
  const [groupA, numberA] = parseTaskId(a);
  const [groupB, numberB] = parseTaskId(b);

  if (groupA !== groupB) {
    return groupA.localeCompare(groupB);
  }

  return numberA - numberB;
}

function parseTaskId(taskId) {
  const match = taskId.match(/^(\D+)-(\d+)$/);
  if (!match) {
    return [taskId, Number.POSITIVE_INFINITY];
  }

  const [, group, number] = match;
  return [group, Number.parseInt(number, 10)];
}

async function readJsonFile(filePath) {
  const payload = await fs.readFile(filePath, 'utf8');
  return JSON.parse(payload);
}

async function collectLocales() {
  const entries = await fs.readdir(DIST_ROOT, { withFileTypes: true });
  return entries.filter((entry) => entry.isDirectory()).map((entry) => entry.name).sort();
}

async function readTaskCounts(locale) {
  const tasksDir = path.join(DIST_ROOT, locale, 'tasks');
  const counts = new Map();

  let entries = [];
  try {
    entries = await fs.readdir(tasksDir, { withFileTypes: true });
  } catch (error) {
    if (error.code === 'ENOENT') {
      return counts;
    }
    throw error;
  }

  for (const entry of entries) {
    if (!entry.isFile() || !entry.name.endsWith('.json')) {
      continue;
    }

    const taskId = entry.name.slice(0, -'.json'.length);
    const filePath = path.join(tasksDir, entry.name);
    const questions = await readJsonFile(filePath);
    counts.set(taskId, Array.isArray(questions) ? questions.length : 0);
  }

  return counts;
}

async function buildSummary() {
  const locales = await collectLocales();

  if (locales.length === 0) {
    throw new Error('No locales found in dist/questions');
  }

  const totals = new Map();
  const countsByLocale = new Map();
  const allTaskIds = new Set();

  for (const locale of locales) {
    const counts = await readTaskCounts(locale);
    countsByLocale.set(locale, counts);

    let total = 0;
    for (const [taskId, count] of counts.entries()) {
      allTaskIds.add(taskId);
      total += count;
    }
    totals.set(locale, total);
  }

  const extraTasks = Array.from(allTaskIds).filter((taskId) => !EXPECTED_TASKS.includes(taskId)).sort(compareTaskIds);
  const sortedTasks = [...EXPECTED_TASKS, ...extraTasks].filter((taskId, index, array) => array.indexOf(taskId) === index);
  const hasZero =
    sortedTasks.some((taskId) =>
      locales.some((locale) => (countsByLocale.get(locale)?.get(taskId) ?? 0) === 0),
    ) || locales.some((locale) => (totals.get(locale) ?? 0) === 0);

  const lines = [];
  lines.push('### Questions dist summary');
  lines.push('');
  lines.push(hasZero ? 'Status: ⚠️ Missing questions detected.' : 'Status: ✅ All tasks have questions.');
  lines.push('');

  for (const locale of locales) {
    lines.push(`- Total (${locale}): ${totals.get(locale) ?? 0}`);
  }

  if (sortedTasks.length > 0) {
    lines.push('');
    lines.push(`| ${['Task', ...locales].join(' | ')} |`);
    lines.push(`| ${['---', ...locales.map(() => '---')].join(' | ')} |`);

    for (const taskId of sortedTasks) {
      const row = [taskId];
      for (const locale of locales) {
        const count = countsByLocale.get(locale)?.get(taskId) ?? 0;
        row.push(count === 0 ? '0 ⚠️' : String(count));
      }
      lines.push(`| ${row.join(' | ')} |`);
    }
  }

  return lines.join('\n');
}

async function main() {
  const summary = await buildSummary();
  console.log(summary);
}

main().catch((error) => {
  console.error('[print-dist-summary] ERROR', error);
  process.exit(1);
});

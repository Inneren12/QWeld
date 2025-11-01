#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const fsp = fs.promises;

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const QUESTIONS_DIR = path.join(ROOT_DIR, 'content', 'questions');
const ARCHIVE_DIR = path.join(ROOT_DIR, 'content', 'archive');

function toPosix(relativePath) {
  return relativePath.split(path.sep).join('/');
}

function stripLocaleSuffix(baseName, locale) {
  const suffix = `__${locale}`;
  if (baseName.endsWith(suffix)) {
    return baseName.slice(0, -suffix.length);
  }
  return baseName;
}

function computeNormalizedKey({ id, taskId, baseName, locale }) {
  const fallback = `Q-${taskId}_${stripLocaleSuffix(baseName, locale)}`;
  const identifier = typeof id === 'string' && id.startsWith('Q-') ? id : fallback;
  return identifier.replace(/_v\d+_\d{2}_[0-9a-fA-F]+$/u, '');
}

function extractVersion(...sources) {
  for (const source of sources) {
    if (typeof source !== 'string') {
      continue;
    }
    const matches = [...source.matchAll(/_v(\d+)/gu)];
    if (matches.length === 0) {
      continue;
    }
    const [, versionDigits] = matches[matches.length - 1];
    const version = Number.parseInt(versionDigits, 10);
    if (!Number.isNaN(version)) {
      return version;
    }
  }
  return -1;
}

async function safeReadDir(targetPath) {
  try {
    return await fsp.readdir(targetPath, { withFileTypes: true });
  } catch (error) {
    if (error.code === 'ENOENT') {
      return [];
    }
    throw error;
  }
}

async function collectQuestionEntries() {
  const locales = await safeReadDir(QUESTIONS_DIR);
  const entries = [];

  for (const localeEntry of locales) {
    if (!localeEntry.isDirectory()) {
      continue;
    }

    const locale = localeEntry.name;
    const localeDir = path.join(QUESTIONS_DIR, locale);
    const tasks = await safeReadDir(localeDir);

    for (const taskEntry of tasks) {
      if (!taskEntry.isDirectory()) {
        continue;
      }

      const taskId = taskEntry.name;
      const taskDir = path.join(localeDir, taskId);
      const files = await safeReadDir(taskDir);

      for (const fileEntry of files) {
        if (!fileEntry.isFile() || !fileEntry.name.endsWith('.json')) {
          continue;
        }

        const fullPath = path.join(taskDir, fileEntry.name);
        const raw = await fsp.readFile(fullPath, 'utf8');
        const payload = JSON.parse(raw);
        const baseName = fileEntry.name.replace(/\.json$/u, '');
        const id = typeof payload.id === 'string' ? payload.id : null;
        const normalizedKey = computeNormalizedKey({ id, taskId, baseName, locale });
        const version = extractVersion(id, baseName);
        const relativePath = toPosix(path.relative(ROOT_DIR, fullPath));

        entries.push({
          locale,
          taskId,
          normalizedKey,
          version,
          fullPath,
          relativePath,
          fileName: fileEntry.name,
        });
      }
    }
  }

  return entries;
}

function groupEntries(entries) {
  const groups = new Map();

  for (const entry of entries) {
    const key = `${entry.locale}::${entry.taskId}::${entry.normalizedKey}`;
    if (!groups.has(key)) {
      groups.set(key, { locale: entry.locale, taskId: entry.taskId, normalizedKey: entry.normalizedKey, items: [] });
    }
    groups.get(key).items.push(entry);
  }

  return [...groups.values()].filter((group) => group.items.length > 1);
}

function chooseCanonical(items) {
  let canonical = null;
  for (const item of items) {
    if (!canonical) {
      canonical = item;
      continue;
    }

    if (item.version > canonical.version) {
      canonical = item;
      continue;
    }

    if (item.version === canonical.version) {
      const compare = item.relativePath.localeCompare(canonical.relativePath);
      if (compare > 0) {
        canonical = item;
      }
    }
  }
  return canonical;
}

function toCsvValue(value) {
  if (value === null || value === undefined) {
    return '';
  }
  const stringValue = String(value);
  if (/[",\n]/u.test(stringValue)) {
    return `"${stringValue.replace(/"/gu, '""')}"`;
  }
  return stringValue;
}

function buildSummaryRows(operations) {
  const header = 'normalized_key,locale,task,kept,moved_count,moved_list';
  const rows = operations.map((operation) => {
    const movedList = operation.moved.map((item) => `${item.relativePath} -> ${item.destinationRelative}`).join(';');
    return [
      toCsvValue(operation.normalizedKey),
      toCsvValue(operation.locale),
      toCsvValue(operation.taskId),
      toCsvValue(operation.kept.relativePath),
      toCsvValue(operation.moved.length),
      toCsvValue(movedList),
    ].join(',');
  });
  return [header, ...rows].join('\n');
}

async function ensureDir(targetPath) {
  await fsp.mkdir(targetPath, { recursive: true });
}

async function main() {
  const args = parseArgs({
    options: {
      'dry-run': { type: 'boolean' },
      apply: { type: 'boolean' },
      'report-dir': { type: 'string' },
    },
    allowPositionals: false,
  });

  const dryRun = Boolean(args.values['dry-run']);
  const apply = Boolean(args.values.apply);

  if ((dryRun && apply) || (!dryRun && !apply)) {
    console.error('Specify exactly one of --dry-run or --apply.');
    process.exit(1);
  }

  const reportRootInput = args.values['report-dir'] ?? process.env.QWELD_PLAG_DEDUPE_REPORT_DIR ?? null;
  const reportRoot = reportRootInput ? path.resolve(ROOT_DIR, reportRootInput) : null;
  const modeName = dryRun ? 'dry-run' : 'apply';
  const reportDir = reportRoot ? path.join(reportRoot, modeName) : null;

  if (reportDir) {
    await ensureDir(reportDir);
  }

  const entries = await collectQuestionEntries();
  const groups = groupEntries(entries);

  const operations = groups.map((group) => {
    const canonical = chooseCanonical(group.items);
    const beforePaths = group.items.map((item) => item.relativePath);
    const moved = group.items
      .filter((item) => item !== canonical)
      .map((item) => {
        const destinationFullPath = path.join(ARCHIVE_DIR, group.locale, group.taskId, item.fileName);
        const destinationRelative = toPosix(path.relative(ROOT_DIR, destinationFullPath));
        return {
          ...item,
          destinationFullPath,
          destinationRelative,
        };
      });

    return {
      locale: group.locale,
      taskId: group.taskId,
      normalizedKey: group.normalizedKey,
      kept: canonical,
      moved,
      beforePaths,
    };
  }).filter((operation) => operation.moved.length > 0);

  if (operations.length === 0) {
    console.log('No duplicate variants detected.');
  } else if (dryRun) {
    for (const operation of operations) {
      console.log(`\n[${operation.locale}/${operation.taskId}] ${operation.normalizedKey}`);
      console.log('Before:');
      console.table(operation.beforePaths.map((file) => ({ file })));
      console.log('After:');
      const rows = [
        { action: 'keep', file: operation.kept.relativePath },
        ...operation.moved.map((item) => ({ action: 'move', file: `${item.relativePath} -> ${item.destinationRelative}` })),
      ];
      console.table(rows);
    }
  }

  if (apply) {
    for (const operation of operations) {
      for (const item of operation.moved) {
        await ensureDir(path.dirname(item.destinationFullPath));
        await fsp.rename(item.fullPath, item.destinationFullPath);
      }
    }
  }

  const csvSummary = buildSummaryRows(operations);
  console.log('\nCSV summary:');
  console.log(csvSummary);

  if (reportDir) {
    const reportData = {
      mode: modeName,
      generatedAt: new Date().toISOString(),
      groups: operations.map((operation) => ({
        normalizedKey: operation.normalizedKey,
        locale: operation.locale,
        task: operation.taskId,
        kept: operation.kept.relativePath,
        moved: operation.moved.map((item) => ({
          source: item.relativePath,
          destination: item.destinationRelative,
        })),
        before: operation.beforePaths,
      })),
    };

    await fsp.writeFile(path.join(reportDir, 'before-after.json'), `${JSON.stringify(reportData, null, 2)}\n`);
    await fsp.writeFile(path.join(reportDir, 'summary.csv'), `${csvSummary}\n`);
  }
}

try {
  await main();
} catch (error) {
  console.error(error);
  process.exit(1);
}

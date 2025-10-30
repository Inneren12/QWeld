#!/usr/bin/env node
import { promises as fs } from 'fs';
import path from 'path';

const ID_PATTERN = /^Q-[0-9A-Za-z_-]+$/;
const CHOICE_PATTERN = /^CHOICE-[0-9A-Za-z_-]+$/;
const EXTRA_KEYS = new Set(['schema', 'version', 'policyVersion', 'locale', 'blockId']);

const args = process.argv.slice(2);
let dryRun = false;
let fileFilter = null;

for (let i = 0; i < args.length; i += 1) {
  const arg = args[i];
  if (arg === '--dry-run') {
    dryRun = true;
  } else if (arg === '--file') {
    const next = args[i + 1];
    if (!next) {
      console.error('Expected a path after --file');
      process.exit(1);
    }
    fileFilter = next;
    i += 1;
  } else {
    console.error(`Unknown argument: ${arg}`);
    process.exit(1);
  }
}

const rootDir = process.cwd();
const questionsDir = path.join(rootDir, 'content', 'questions');

async function collectQuestionFiles() {
  if (fileFilter) {
    const absPath = path.isAbsolute(fileFilter) ? fileFilter : path.join(rootDir, fileFilter);
    return [absPath];
  }
  const files = [];
  async function walk(dir) {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    for (const entry of entries) {
      const entryPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        await walk(entryPath);
      } else if (entry.isFile() && entry.name.endsWith('.json')) {
        files.push(entryPath);
      }
    }
  }
  await walk(questionsDir);
  files.sort();
  return files;
}

function normaliseQuestionId(currentId, filePath) {
  if (typeof currentId === 'string' && ID_PATTERN.test(currentId)) {
    return currentId;
  }
  const baseName = path.basename(filePath, '.json');
  const fallbackSource = typeof currentId === 'string' && currentId.trim() !== '' ? currentId : baseName.split('__')[0];
  const stripped = fallbackSource.replace(/__.+$/, '');
  let core = stripped.replace(/[^0-9A-Za-z_-]/g, '_');
  core = core.replace(/_+/g, '_').replace(/^_+/, '').replace(/_+$/, '');
  if (!core) {
    core = 'QUESTION';
  }
  if (core.startsWith('Q-')) {
    core = core.slice(2);
  }
  return `Q-${core}`;
}

function buildChoiceMapping(choices) {
  const mapping = new Map();
  choices.forEach((choice, index) => {
    const newId = `CHOICE-${index + 1}`;
    const rawKey =
      typeof choice.id === 'string' ? choice.id : String(choice.id ?? index + 1);
    const key = rawKey.trim();
    mapping.set(key, newId);
    if (key !== rawKey) {
      mapping.set(rawKey, newId);
    }
  });
  return mapping;
}

function resolveChoiceId(originalId, choicesLength, mapping) {
  if (originalId == null) {
    return null;
  }
  const originalKey =
    typeof originalId === 'string' ? originalId.trim() : String(originalId);
  if (mapping.has(originalKey)) {
    return mapping.get(originalKey);
  }
  const upper = originalKey.toUpperCase();
  if (upper.length === 1 && upper >= 'A' && upper <= 'Z') {
    const offset = upper.charCodeAt(0) - 'A'.charCodeAt(0);
    if (offset >= 0 && offset < choicesLength) {
      return `CHOICE-${offset + 1}`;
    }
  }
  if (/^\d+$/.test(originalKey)) {
    const idx = Number(originalKey);
    if (idx >= 1 && idx <= choicesLength) {
      return `CHOICE-${idx}`;
    }
  }
  return null;
}

function updateRationales(rationales, choicesLength, mapping) {
  if (!rationales || typeof rationales !== 'object') {
    return rationales;
  }
  const updatedEntries = [];
  for (const [key, value] of Object.entries(rationales)) {
    const resolved = resolveChoiceId(key, choicesLength, mapping);
    if (!resolved) {
      throw new Error(`Unable to map rationale key "${key}" to new choice id`);
    }
    updatedEntries.push([resolved, value]);
  }
  const ordered = updatedEntries.sort((a, b) => a[0].localeCompare(b[0]));
  const next = {};
  for (const [key, value] of ordered) {
    next[key] = value;
  }
  return next;
}

async function migrateFile(filePath) {
  const contents = await fs.readFile(filePath, 'utf8');
  let data;
  try {
    data = JSON.parse(contents);
  } catch (error) {
    throw new Error(`Failed to parse JSON: ${filePath}\n${error.message}`);
  }

  let changed = false;

  for (const extraKey of EXTRA_KEYS) {
    if (extraKey in data) {
      delete data[extraKey];
      changed = true;
    }
  }

  const normalisedId = normaliseQuestionId(data.id, filePath);
  if (data.id !== normalisedId) {
    data.id = normalisedId;
    changed = true;
  }

  if (!Array.isArray(data.choices)) {
    throw new Error(`Missing choices array in ${filePath}`);
  }

  const mapping = buildChoiceMapping(data.choices);
  const newChoices = data.choices.map((choice, index) => {
    const newId = `CHOICE-${index + 1}`;
    const updated = { ...choice, id: newId };
    if (!CHOICE_PATTERN.test(newId)) {
      throw new Error(`Generated invalid choice id ${newId} for ${filePath}`);
    }
    if (choice.id !== newId) {
      changed = true;
    }
    return updated;
  });
  data.choices = newChoices;

  const resolvedCorrectId = resolveChoiceId(data.correctId, data.choices.length, mapping);
  if (!resolvedCorrectId) {
    throw new Error(`Unable to resolve correctId "${data.correctId}" in ${filePath}`);
  }
  if (data.correctId !== resolvedCorrectId) {
    data.correctId = resolvedCorrectId;
    changed = true;
  }

  if (data.rationales) {
    const updatedRationales = updateRationales(data.rationales, data.choices.length, mapping);
    if (JSON.stringify(updatedRationales) !== JSON.stringify(data.rationales)) {
      data.rationales = updatedRationales;
      changed = true;
    }
  }

  const serialised = `${JSON.stringify(data, null, 2)}\n`;
  if (!dryRun && changed) {
    await fs.writeFile(filePath, serialised, 'utf8');
  }

  const relPath = path.relative(rootDir, filePath);
  if (changed) {
    console.log(`[migrate] file=${relPath} status=${dryRun ? 'needs-update' : 'updated'}`);
  } else {
    console.log(`[migrate] file=${relPath} status=ok`);
  }
}

(async () => {
  try {
    const files = await collectQuestionFiles();
    if (files.length === 0) {
      console.log('[migrate] no question files found');
      return;
    }
    for (const file of files) {
      await migrateFile(file);
    }
  } catch (error) {
    console.error(`[migrate] ERROR: ${error.message}`);
    process.exit(1);
  }
})();

#!/usr/bin/env node
import fs from 'node:fs';
import { readFile, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const SCHEMA_PATH = path.join(ROOT_DIR, 'schemas', 'question.schema.json');
const BAN_PATTERN = /(real exam|red seal exam question|actual exam)/i;

function printError(message) {
  console.error(message);
}

async function readInputFiles(argv) {
  const files = new Set();
  let filesFrom;

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--files-from') {
      filesFrom = argv[i + 1];
      i += 1;
      if (!filesFrom) {
        throw new Error('--files-from requires a value');
      }
    } else if (arg.startsWith('--')) {
      throw new Error(`Unknown option: ${arg}`);
    } else {
      files.add(arg);
    }
  }

  if (filesFrom) {
    const content = await readFile(filesFrom, 'utf8');
    content
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .forEach((line) => files.add(line));
  }

  if (!filesFrom && files.size === 0 && !process.stdin.isTTY) {
    const chunks = [];
    for await (const chunk of process.stdin) {
      chunks.push(chunk);
    }
    const input = Buffer.concat(chunks).toString('utf8');
    input
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .forEach((line) => files.add(line));
  }

  return Array.from(files);
}

const require = createRequire(import.meta.url);

async function ensureModule(moduleName, importPromise) {
  try {
    return await importPromise;
  } catch (error) {
    if (error && (error.code === 'ERR_MODULE_NOT_FOUND' || error.code === 'MODULE_NOT_FOUND')) {
      try {
        const required = require(moduleName);
        return { default: required?.default ?? required };
      } catch (requireError) {
        if (requireError && (requireError.code === 'ERR_MODULE_NOT_FOUND' || requireError.code === 'MODULE_NOT_FOUND')) {
          printError(`[questions] ERROR: missing dependency \"${moduleName}\"`);
          printError('[questions] Hint: install with `npm i -D -w . ajv ajv-formats --no-save --no-package-lock`');
          process.exit(1);
        }
        throw requireError;
      }
    }
    throw error;
  }
}

async function loadSchema() {
  try {
    await access(SCHEMA_PATH, fs.constants.R_OK);
  } catch (error) {
    printError(`[questions] ERROR: schema not found at ${path.relative(ROOT_DIR, SCHEMA_PATH)}`);
    process.exit(1);
  }
  const raw = await readFile(SCHEMA_PATH, 'utf8');
  return JSON.parse(raw);
}

async function main() {
  const files = await readInputFiles(process.argv.slice(2));

  if (files.length === 0) {
    console.log('[questions] INFO: no question files to validate');
    return;
  }

  const [{ default: Ajv }, { default: addFormats }] = await Promise.all([
    ensureModule('ajv', import('ajv')),
    ensureModule('ajv-formats', import('ajv-formats')),
  ]);

  const schema = await loadSchema();
  const ajv = new Ajv({ strict: false, allErrors: true, $data: true });
  addFormats(ajv);
  const validate = ajv.compile(schema);

  for (const filePath of files) {
    const absPath = path.isAbsolute(filePath) ? filePath : path.join(ROOT_DIR, filePath);
    const relPath = path.relative(ROOT_DIR, absPath);

    let raw;
    try {
      raw = await readFile(absPath, 'utf8');
    } catch (error) {
      printError(`[questions] ERROR: failed to read ${relPath}`);
      printError(String(error));
      process.exit(1);
    }

    if (BAN_PATTERN.test(raw)) {
      const match = raw.match(BAN_PATTERN);
      printError(`[questions] ERROR: banned phrase found: "${match?.[0]}" in ${relPath}`);
      process.exit(1);
    }

    let data;
    try {
      data = JSON.parse(raw);
    } catch (error) {
      printError(`[questions] ERROR: invalid JSON in ${relPath}`);
      printError(String(error));
      process.exit(1);
    }

    const valid = validate(data);
    if (!valid) {
      printError(`[questions] ERROR: schema validation failed for ${relPath}`);
      if (Array.isArray(validate.errors)) {
        const detail = ajv.errorsText(validate.errors, { separator: '\n  ' });
        printError(`  ${detail}`);
      }
      process.exit(1);
    }

    const correctId = data.correctId;
    if (correctId === null || correctId === undefined || correctId === '') {
      printError(`[questions] ERROR: missing correctId in ${relPath}`);
      process.exit(1);
    }

    const rationales = data.rationales ?? {};
    if (typeof rationales !== 'object' || !(correctId in rationales)) {
      printError(`[questions] ERROR: rationale missing for correctId=${correctId} file=${relPath}`);
      process.exit(1);
    }

    console.log(`[questions] file=${relPath} schema=ok rationale=ok`);
  }
}

main().catch((error) => {
  printError('[questions] ERROR: unexpected failure');
  printError(String(error));
  process.exit(1);
});

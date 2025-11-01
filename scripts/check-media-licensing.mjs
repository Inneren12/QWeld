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
const MEDIA_DIR = path.join(ROOT, 'media');
const LOG_DIR = path.join(ROOT, 'logs');
const REPORT_PATH = path.join(LOG_DIR, 'media-licensing-report.md');

const DISALLOWED_LICENSE_VALUES = new Set(['unknown', 'unspecified', 'n/a', 'none']);

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
  const files = [];
  const stack = [dir];
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

function relPath(p) {
  return path.relative(ROOT, p) || path.basename(p);
}

function isUrl(value) {
  return typeof value === 'string' && /^https?:\/\//i.test(value.trim());
}

function sanitize(value) {
  if (typeof value !== 'string') {
    return '';
  }
  return value.trim();
}

function extractInlineLicense(mediaItem) {
  const inline = mediaItem && typeof mediaItem === 'object' ? mediaItem : {};
  let license;
  if (typeof inline.license === 'string') {
    license = inline.license;
  } else if (inline.license && typeof inline.license === 'object') {
    license = inline.license.license ?? inline.license.type ?? inline.license.name;
  }
  const source = inline.source ?? (inline.license && typeof inline.license === 'object' ? inline.license.source : undefined);
  const author = inline.author ?? (inline.license && typeof inline.license === 'object' ? inline.license.author : undefined);
  return {
    license: sanitize(license),
    source: sanitize(source),
    author: sanitize(author),
  };
}

function validateLicenseRecord(record) {
  const missing = [];
  if (!record.license) {
    missing.push('license');
  }
  if (!record.source) {
    missing.push('source');
  }
  if (!record.author) {
    missing.push('author');
  }
  if (missing.length > 0) {
    return { ok: false, reason: `missing fields: ${missing.join(', ')}` };
  }
  if (DISALLOWED_LICENSE_VALUES.has(record.license.toLowerCase())) {
    return { ok: false, reason: `disallowed license value: ${record.license}` };
  }
  return { ok: true };
}

function deriveMediaReference(mediaItem) {
  if (!mediaItem || typeof mediaItem !== 'object') {
    return { ref: '', id: null };
  }
  const ref = sanitize(mediaItem.uri ?? mediaItem.value ?? mediaItem.path ?? mediaItem.href ?? '');
  const rawId = sanitize(mediaItem.licenseId ?? mediaItem.id ?? '');
  if (rawId) {
    return { ref, id: rawId };
  }
  if (!ref) {
    return { ref: '', id: null };
  }
  const withoutQuery = ref.split(/[?#]/)[0];
  const base = path.basename(withoutQuery);
  if (!base) {
    return { ref, id: null };
  }
  const clean = base.includes('.') ? base.replace(/\.[^.]+$/, '') : base;
  return { ref, id: clean || base };
}

async function readJsonFile(p) {
  const raw = await fs.readFile(p, 'utf8');
  try {
    return JSON.parse(raw);
  } catch (err) {
    throw new Error(`Failed to parse ${relPath(p)}: ${err.message}`);
  }
}

async function loadSidecar(licenseId) {
  const filename = `${licenseId}.license.json`;
  const candidate = path.join(MEDIA_DIR, filename);
  if (!(await pathExists(candidate))) {
    return { found: false, path: candidate };
  }
  const data = await readJsonFile(candidate);
  return { found: true, path: candidate, data };
}

async function collectMediaFiles() {
  const sources = [
    { type: 'question', dir: QUESTION_DIR },
    { type: 'explanation', dir: EXPLANATION_DIR },
  ];
  const items = [];
  for (const source of sources) {
    const files = await walkJsonFiles(source.dir);
    for (const file of files) {
      const raw = await fs.readFile(file, 'utf8');
      let data;
      try {
        data = JSON.parse(raw);
      } catch (err) {
        console.error(`[media_lic_fail] file=${relPath(file)} reason=json_parse error=${err.message}`);
        continue;
      }
      const media = Array.isArray(data.media) ? data.media : [];
      media.forEach((mediaItem, index) => {
        items.push({
          file,
          type: source.type,
          index,
          mediaItem,
        });
      });
    }
  }
  return items;
}

async function main() {
  const records = await collectMediaFiles();
  let checked = 0;
  let missing = 0;
  let invalid = 0;
  const missingRecords = [];
  const invalidRecords = [];

  for (const record of records) {
    checked += 1;
    const { file, mediaItem, index } = record;
    const { ref, id } = deriveMediaReference(mediaItem);
    const inline = extractInlineLicense(mediaItem);
    const isExternal = isUrl(ref);

    if (isExternal) {
      const validation = validateLicenseRecord(inline);
      if (!validation.ok) {
        invalid += 1;
        const issue = validation.reason || 'missing license data for external media';
        invalidRecords.push({ file, index, ref, issue });
        console.error(`[media_lic_fail] file=${relPath(file)} mediaRef=${ref || '(external)'} issue=${issue}`);
      }
      continue;
    }

    if (!id) {
      invalid += 1;
      const issue = 'unable to derive media id for sidecar lookup';
      invalidRecords.push({ file, index, ref, issue });
      console.error(`[media_lic_fail] file=${relPath(file)} mediaRef=${ref || '(local)'} issue=${issue}`);
      continue;
    }

    const sidecar = await loadSidecar(id);
    if (!sidecar.found) {
      missing += 1;
      missingRecords.push({ file, index, ref: id, path: sidecar.path });
      console.error(`[media_lic_fail] file=${relPath(file)} mediaRef=${id} issue=missing_sidecar path=${relPath(sidecar.path)}`);
      continue;
    }
    try {
      const validation = validateLicenseRecord({
        license: sanitize(sidecar.data.license),
        source: sanitize(sidecar.data.source),
        author: sanitize(sidecar.data.author),
      });
      if (!validation.ok) {
        invalid += 1;
        const issue = validation.reason || 'invalid sidecar payload';
        invalidRecords.push({ file, index, ref: id, issue, sidecar: sidecar.path });
        console.error(`[media_lic_fail] file=${relPath(file)} mediaRef=${id} issue=${issue}`);
      }
    } catch (err) {
      invalid += 1;
      const issue = err.message;
      invalidRecords.push({ file, index, ref: id, issue, sidecar: sidecar.path });
      console.error(`[media_lic_fail] file=${relPath(file)} mediaRef=${id} issue=${issue}`);
    }
  }

  console.log(`[media_lic] checked=${checked} missing=${missing} invalid=${invalid}`);

  await fs.mkdir(LOG_DIR, { recursive: true });
  const lines = [];
  lines.push('# Media licensing report');
  lines.push('');
  lines.push(`- Media entries checked: ${checked}`);
  lines.push(`- Missing sidecars: ${missing}`);
  lines.push(`- Invalid license records: ${invalid}`);
  lines.push('');

  if (missingRecords.length > 0) {
    lines.push('## ❌ Missing sidecar files');
    lines.push('');
    lines.push('| File | Media index | Expected sidecar | Reference |');
    lines.push('| --- | --- | --- | --- |');
    for (const item of missingRecords) {
      lines.push(`| ${relPath(item.file)} | ${item.index} | ${relPath(item.path)} | ${item.ref} |`);
    }
    lines.push('');
  }

  if (invalidRecords.length > 0) {
    lines.push('## ❌ Invalid license information');
    lines.push('');
    lines.push('| File | Media index | Reference | Issue |');
    lines.push('| --- | --- | --- | --- |');
    for (const item of invalidRecords) {
      const ref = item.ref || '(external)';
      lines.push(`| ${relPath(item.file)} | ${item.index} | ${ref} | ${item.issue} |`);
    }
    lines.push('');
  }

  await fs.writeFile(REPORT_PATH, lines.join('\n'), 'utf8');

  if (missing > 0 || invalid > 0) {
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error('[media_lic_fail] fatal', err);
  process.exitCode = 1;
});

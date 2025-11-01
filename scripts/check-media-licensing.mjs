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
const MEDIA_LICENSE_DIR = path.join(CONTENT_DIR, 'media');
const LOG_DIR = path.join(ROOT_DIR, 'logs');

const REQUIRED_LICENSE_FIELDS = ['license', 'source', 'author'];
const FORBIDDEN_LICENSE_VALUES = new Set(['unknown', 'unspecified', 'n/a', 'none']);

const isExternalUri = (value) => /^https?:\/\//iu.test(value);

async function ensureLogDir() {
  await fs.mkdir(LOG_DIR, { recursive: true });
}

async function readJson(filePath) {
  const payload = await fs.readFile(filePath, 'utf8');
  return JSON.parse(payload);
}

async function collectJsonFiles(dirPath, files) {
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
      await collectJsonFiles(fullPath, files);
      continue;
    }

    if (!entry.isFile() || !entry.name.endsWith('.json')) {
      continue;
    }

    files.push(fullPath);
  }
}

function normalizeString(value) {
  return String(value ?? '').trim();
}

function validateInlineLicense(licenseObj) {
  const issues = [];

  for (const field of REQUIRED_LICENSE_FIELDS) {
    const value = normalizeString(licenseObj?.[field]);
    if (!value) {
      issues.push(`missing_field:${field}`);
      continue;
    }

    if (FORBIDDEN_LICENSE_VALUES.has(value.toLowerCase())) {
      issues.push(`forbidden_value:${field}`);
    }
  }

  return issues;
}

async function readLicenseSidecar(mediaId) {
  const licensePath = path.join(MEDIA_LICENSE_DIR, `${mediaId}.license.json`);

  try {
    const payload = await readJson(licensePath);
    return { payload, licensePath };
  } catch (error) {
    if (error.code === 'ENOENT') {
      return { payload: null, licensePath, missing: true };
    }
    throw error;
  }
}

function deriveMediaId(entry, uri) {
  if (typeof entry?.id === 'string' && entry.id.trim().length > 0) {
    return entry.id.trim();
  }

  if (typeof uri === 'string' && uri.trim().length > 0) {
    const cleanUri = uri.trim();
    const baseName = path.basename(cleanUri);
    if (!baseName) {
      return null;
    }
    return baseName.replace(/\.[^.]+$/u, '');
  }

  return null;
}

function describeMedia(entry) {
  if (entry && typeof entry.caption === 'string' && entry.caption.trim().length > 0) {
    return entry.caption.trim();
  }

  if (entry && typeof entry.type === 'string') {
    return entry.type;
  }

  return 'media-item';
}

async function buildReportMarkdown(summary) {
  const lines = [
    '# Media Licensing Report',
    '',
    `- Items checked: ${summary.itemsChecked}`,
    `- Missing issues: ${summary.missingCount}`,
    `- Invalid issues: ${summary.invalidCount}`,
    `- Total issues: ${summary.issues.length}`,
    '',
  ];

  if (summary.issues.length === 0) {
    lines.push('✅ All media entries include required licensing information.');
  } else {
    lines.push('| File | Media description | Issue | Details |');
    lines.push('| --- | --- | --- | --- |');
    for (const issue of summary.issues) {
      lines.push(`| ${issue.file} | ${issue.description} | ${issue.issue} | ${issue.details} |`);
    }
  }

  lines.push('');
  return `${lines.join('\n')}\n`;
}

async function main() {
  const jsonFiles = [];
  await collectJsonFiles(QUESTIONS_DIR, jsonFiles);
  await collectJsonFiles(EXPLANATIONS_DIR, jsonFiles);

  let itemsChecked = 0;
  const issues = [];

  for (const filePath of jsonFiles) {
    let payload;
    try {
      payload = await readJson(filePath);
    } catch (error) {
      console.error(`[media_lic_fail] file=${path.relative(ROOT_DIR, filePath)} reason=invalid_json message="${error.message}"`);
      issues.push({
        file: path.relative(ROOT_DIR, filePath),
        description: '—',
        issue: 'invalid_json',
        details: error.message,
        category: 'invalid',
      });
      continue;
    }

    const mediaEntries = Array.isArray(payload.media) ? payload.media : [];

    for (let index = 0; index < mediaEntries.length; index += 1) {
      const entry = mediaEntries[index];
      const relativeFile = path.relative(ROOT_DIR, filePath);
      const uri = normalizeString(entry?.uri ?? entry?.value ?? '');
      const description = describeMedia(entry);
      itemsChecked += 1;

      if (!uri) {
        issues.push({
          file: relativeFile,
          description,
          issue: 'missing_uri',
          details: `media[${index}] must provide a uri/value field`,
          category: 'missing',
        });
        console.log(`[media_lic_fail] file=${relativeFile} index=${index} reason=missing_uri`);
        continue;
      }

      if (isExternalUri(uri)) {
        const inlineIssues = validateInlineLicense(entry?.license);
        if (inlineIssues.length > 0) {
          issues.push({
            file: relativeFile,
            description,
            issue: 'inline_license',
            details: inlineIssues.join('; '),
            category: inlineIssues.some((item) => item.startsWith('missing_field')) ? 'missing' : 'invalid',
          });
          console.log(`[media_lic_fail] file=${relativeFile} index=${index} reason=inline_license details=${inlineIssues.join('|')}`);
        }
        continue;
      }

      const mediaId = deriveMediaId(entry, uri);
      if (!mediaId) {
        issues.push({
          file: relativeFile,
          description,
          issue: 'media_id',
          details: `Unable to derive media id for uri "${uri}"`,
          category: 'missing',
        });
        console.log(`[media_lic_fail] file=${relativeFile} index=${index} reason=media_id uri="${uri}"`);
        continue;
      }

      const sidecar = await readLicenseSidecar(mediaId);
      if (sidecar.missing) {
        issues.push({
          file: relativeFile,
          description,
          issue: 'missing_sidecar',
          details: `${path.relative(ROOT_DIR, sidecar.licensePath)}`,
          category: 'missing',
        });
        console.log(`[media_lic_fail] file=${relativeFile} index=${index} reason=missing_sidecar path=${path.relative(ROOT_DIR, sidecar.licensePath)}`);
        continue;
      }

      const inlineIssues = validateInlineLicense(sidecar.payload);
      if (inlineIssues.length > 0) {
        issues.push({
          file: relativeFile,
          description,
          issue: 'sidecar_fields',
          details: inlineIssues.join('; '),
          category: inlineIssues.some((item) => item.startsWith('missing_field')) ? 'missing' : 'invalid',
        });
        console.log(`[media_lic_fail] file=${relativeFile} index=${index} reason=sidecar_fields details=${inlineIssues.join('|')}`);
      }
    }
  }

  const missingCount = issues.filter((item) => item.category === 'missing').length;
  const invalidCount = issues.filter((item) => item.category === 'invalid').length;

  console.log(`[media_lic] checked=${itemsChecked} missing=${missingCount} invalid=${invalidCount}`);

  await ensureLogDir();
  const reportMarkdown = await buildReportMarkdown({ itemsChecked, missingCount, invalidCount, issues });
  await fs.writeFile(path.join(LOG_DIR, 'media-licensing-report.md'), reportMarkdown, 'utf8');

  if (issues.length > 0) {
    process.exitCode = 1;
  }
}

await main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

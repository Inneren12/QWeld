#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const targetDir = process.argv[2] || './plag';

if (!fs.existsSync(targetDir)) {
  console.log(`[plag] directory not found: ${targetDir}`);
  process.exit(0);
}

const shardFiles = fs
  .readdirSync(targetDir, { withFileTypes: true })
  .filter((entry) => entry.isFile() && entry.name.endsWith('.json'))
  .map((entry) => entry.name)
  .sort();

if (shardFiles.length === 0) {
  console.log('[plag] no shard reports found');
  process.exit(0);
}

const merged = { duplicates: [] };

for (const fileName of shardFiles) {
  const fullPath = path.join(targetDir, fileName);
  try {
    const payload = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
    if (Array.isArray(payload.duplicates)) {
      merged.duplicates.push(...payload.duplicates);
    }
  } catch (error) {
    console.warn(`[plag] failed to read shard ${fileName}:`, error);
  }
}

const seen = new Set();
const unique = [];

for (const entry of merged.duplicates) {
  const aFile = entry?.a?.file ?? '';
  const bFile = entry?.b?.file ?? '';
  const ordered = [aFile, bFile].sort();
  const overlap = Number.isFinite(entry?.overlap) ? entry.overlap : '';
  const basis = Number.isFinite(entry?.basis) ? entry.basis : '';
  const similarity = Number.isFinite(entry?.similarity) ? entry.similarity : '';
  const key = `${ordered[0]}|${ordered[1]}|${overlap}|${basis}|${similarity}`;

  if (seen.has(key)) {
    continue;
  }
  seen.add(key);
  unique.push(entry);
}

unique.sort((left, right) => Number(right?.similarity ?? 0) - Number(left?.similarity ?? 0));

const output = { duplicates: unique };
fs.writeFileSync('plag-merged.json', `${JSON.stringify(output, null, 2)}\n`);
console.log(`[plag] merged ${shardFiles.length} shards → ${unique.length} findings`);

if (process.env.GITHUB_OUTPUT) {
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `duplicates=${unique.length}\n`);
}

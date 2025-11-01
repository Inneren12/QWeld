import fs from 'node:fs';
import path from 'node:path';

const dir = process.argv[2] || './plag';
if (!fs.existsSync(dir)) {
  console.log(`[plag] no shards dir: ${dir}; nothing to merge`);
  process.exit(0);
}

const files = fs.readdirSync(dir).filter((file) => file.endsWith('.json'));
if (files.length === 0) {
  console.log('[plag] no shard artifacts found; skip merge');
  process.exit(0);
}

const out = { duplicates: [] };
for (const file of files) {
  try {
    const payload = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
    if (Array.isArray(payload.duplicates)) {
      out.duplicates.push(...payload.duplicates);
    }
  } catch (error) {
    console.warn(`[plag] skip broken shard ${file}: ${error?.message ?? error}`);
  }
}

const seen = new Set();
out.duplicates = out.duplicates.filter((entry) => {
  const key = `${entry.a?.file}|${entry.b?.file}|${entry.metric}`;
  if (seen.has(key)) {
    return false;
  }
  seen.add(key);
  return true;
});

fs.writeFileSync('plag-merged.json', JSON.stringify(out, null, 2));
console.log(`[plag] merged ${files.length} shards → ${out.duplicates.length} findings`);

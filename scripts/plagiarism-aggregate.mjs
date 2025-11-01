#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const fsp = fs.promises;

async function collectJsonFiles(targetDir) {
  const result = [];
  const queue = [targetDir];

  while (queue.length > 0) {
    const current = queue.pop();
    let entries;
    try {
      entries = await fsp.readdir(current, { withFileTypes: true });
    } catch (error) {
      if (error.code === 'ENOENT') {
        continue;
      }
      throw error;
    }

    for (const entry of entries) {
      const entryPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        queue.push(entryPath);
        continue;
      }

      if (!entry.isFile()) {
        continue;
      }

      if (/^plag-shard-.*\.json$/u.test(entry.name)) {
        result.push(entryPath);
      }
    }
  }

  return result;
}

function makeDuplicateKey(match) {
  const left = match?.a?.file ?? '';
  const right = match?.b?.file ?? '';
  return left < right ? `${left}::${right}` : `${right}::${left}`;
}

async function loadReport(filePath) {
  const content = await fsp.readFile(filePath, 'utf8');
  try {
    return JSON.parse(content);
  } catch (error) {
    throw new Error(`Unable to parse ${filePath}: ${error.message}`);
  }
}

async function main() {
  const targetDir = process.argv[2] ? path.resolve(process.cwd(), process.argv[2]) : process.cwd();
  const files = await collectJsonFiles(targetDir);

  if (files.length === 0) {
    console.log('No plagiarism shard reports found. Skipping aggregation.');
    return null;
  }

  const duplicatesMap = new Map();
  const shardSummaries = [];

  for (const filePath of files) {
    const report = await loadReport(filePath);
    shardSummaries.push({
      file: filePath,
      documents: report?.documents ?? null,
      comparedPairs: report?.comparedPairs ?? null,
      duplicates: Array.isArray(report?.duplicates) ? report.duplicates.length : null,
      shardIndex: report?.shardIndex ?? null,
      shardCount: report?.shardCount ?? null,
      timedOut: report?.timedOut ?? null,
    });

    const duplicates = Array.isArray(report?.duplicates) ? report.duplicates : [];
    for (const duplicate of duplicates) {
      const key = makeDuplicateKey(duplicate);
      if (!duplicatesMap.has(key)) {
        duplicatesMap.set(key, duplicate);
      }
    }
  }

  const mergedReport = {
    generatedAt: new Date().toISOString(),
    shardReports: shardSummaries,
    duplicates: Array.from(duplicatesMap.values()),
  };

  const outputPath = path.resolve(process.cwd(), 'plag-merged.json');
  await fsp.writeFile(outputPath, JSON.stringify(mergedReport, null, 2));
  console.log(`Merged report written to ${outputPath}`);
  console.log(`Total duplicates: ${mergedReport.duplicates.length}`);

  return mergedReport;
}

await main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

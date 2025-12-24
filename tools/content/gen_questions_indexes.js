#!/usr/bin/env node
/* eslint-disable no-console */
"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const zlib = require("zlib");

function sha256(buf) {
  return crypto.createHash("sha256").update(buf).digest("hex");
}

function readFileBytes(p) {
  return fs.readFileSync(p);
}

function readJsonFile(p) {
  return JSON.parse(fs.readFileSync(p, "utf8"));
}

function writeJsonFile(p, obj) {
  const txt = JSON.stringify(obj, null, 2) + "\n";
  fs.writeFileSync(p, txt, "utf8");
}

function exists(p) {
  try {
    fs.accessSync(p);
    return true;
  } catch {
    return false;
  }
}

function isDir(p) {
  try {
    return fs.statSync(p).isDirectory();
  } catch {
    return false;
  }
}

function findRepoRoot(startDir) {
  let cur = path.resolve(startDir);
  while (true) {
    if (
      exists(path.join(cur, "settings.gradle.kts")) ||
      exists(path.join(cur, "settings.gradle")) ||
      exists(path.join(cur, "gradlew")) ||
      exists(path.join(cur, ".git"))
    ) {
      return cur;
    }
    const parent = path.dirname(cur);
    if (parent === cur) return path.resolve(startDir);
    cur = parent;
  }
}

function listDirSorted(p) {
  return fs.readdirSync(p).slice().sort();
}

function walkFilesSorted(rootDir) {
  const out = [];
  function rec(dir) {
    const names = listDirSorted(dir);
    for (const name of names) {
      const abs = path.join(dir, name);
      const st = fs.statSync(abs);
      if (st.isDirectory()) rec(abs);
      else if (st.isFile()) out.push(abs);
    }
  }
  rec(rootDir);
  return out;
}

function toPosix(p) {
  return p.split(path.sep).join("/");
}

function parseArgs(argv) {
  const args = {
    repoRoot: null,
    assetsRoot: null,
    generatedAt: "keep", // keep | epoch | now | <iso>
    dryRun: false,
  };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--dry-run") args.dryRun = true;
    else if (a === "--repo-root") args.repoRoot = argv[++i];
    else if (a === "--assets-root") args.assetsRoot = argv[++i];
    else if (a === "--generatedAt") args.generatedAt = argv[++i];
    else {
      console.error(`Unknown arg: ${a}`);
      process.exit(2);
    }
  }
  return args;
}

function pickGeneratedAt(rootIndexPath, mode) {
  // mode:
  // - keep: reuse existing generatedAt if present, else SOURCE_DATE_EPOCH, else epoch
  // - epoch: SOURCE_DATE_EPOCH or epoch
  // - now: current time
  // - explicit ISO string
  const epochIso = new Date(0).toISOString();

  if (mode === "now") return new Date().toISOString();

  if (mode === "epoch") {
    const sde = process.env.SOURCE_DATE_EPOCH;
    if (sde && /^\d+$/.test(sde)) return new Date(Number(sde) * 1000).toISOString();
    return epochIso;
  }

  if (mode === "keep") {
    if (exists(rootIndexPath)) {
      try {
        const prev = readJsonFile(rootIndexPath);
        if (prev && typeof prev.generatedAt === "string" && prev.generatedAt.length > 0) {
          return prev.generatedAt;
        }
      } catch {
        // ignore
      }
    }
    const sde = process.env.SOURCE_DATE_EPOCH;
    if (sde && /^\d+$/.test(sde)) return new Date(Number(sde) * 1000).toISOString();
    return epochIso;
  }

  // explicit ISO
  return mode;
}

function extractMetaFromExistingLocaleIndex(localeIndexPath, locale) {
  if (!exists(localeIndexPath)) {
    throw new Error(
      `Missing ${localeIndexPath}. Cannot infer blueprintId/bankVersion for locale=${locale}.`
    );
  }
  const j = readJsonFile(localeIndexPath);

  // Preferred (manifest-type):
  const rootBlueprint = typeof j.blueprintId === "string" ? j.blueprintId : null;
  const rootBank = typeof j.bankVersion === "string" ? j.bankVersion : null;
  if (rootBlueprint && rootBank) return { blueprintId: rootBlueprint, bankVersion: rootBank };

  // Hybrid fallback: maybe locales[locale].blueprintId/bankVersion
  const loc = j && j.locales && j.locales[locale];
  const hb = loc && typeof loc.blueprintId === "string" ? loc.blueprintId : null;
  const hv = loc && typeof loc.bankVersion === "string" ? loc.bankVersion : null;
  if (hb && hv) return { blueprintId: hb, bankVersion: hv };

  // Another hybrid: locales[locale] itself is manifest-ish
  const hb2 = loc && typeof loc.blueprintId === "string" ? loc.blueprintId : null;
  const hv2 = loc && typeof loc.bankVersion === "string" ? loc.bankVersion : null;
  if (hb2 && hv2) return { blueprintId: hb2, bankVersion: hv2 };

  throw new Error(
    `Cannot extract blueprintId/bankVersion from ${localeIndexPath} for locale=${locale}.`
  );
}

function countQuestionsFromTaskJsonBytes(filePath, rawBytes) {
  // Deterministic + tolerant counter.
  // Reads JSON (decompress if .gz) and tries:
  // - array length
  // - obj.questions/items/data arrays
  // fallback 0
  let bytes = rawBytes;
  if (filePath.endsWith(".gz")) {
    bytes = zlib.gunzipSync(rawBytes);
  }
  const txt = bytes.toString("utf8");
  let j;
  try {
    j = JSON.parse(txt);
  } catch {
    return 0;
  }
  if (Array.isArray(j)) return j.length;

  if (j && typeof j === "object") {
    if (Array.isArray(j.questions)) return j.questions.length;
    if (Array.isArray(j.items)) return j.items.length;
    if (Array.isArray(j.data)) return j.data.length;
  }
  return 0;
}

function main() {
  const args = parseArgs(process.argv);

  const repoRoot = args.repoRoot ? path.resolve(args.repoRoot) : findRepoRoot(process.cwd());
  const assetsRoot = args.assetsRoot
    ? path.resolve(args.assetsRoot)
    : path.join(repoRoot, "app-android", "src", "main", "assets");
  const questionsRoot = path.join(assetsRoot, "questions");
  const rootIndexPath = path.join(questionsRoot, "index.json");

  if (!isDir(questionsRoot)) {
    console.error(`questions root not found: ${questionsRoot}`);
    process.exit(1);
  }

  // Locales: directories under questions/, ignore files (including index.json).
  const locales = listDirSorted(questionsRoot).filter((name) => isDir(path.join(questionsRoot, name)));

  if (locales.length === 0) {
    console.error(`No locales found under: ${questionsRoot}`);
    process.exit(1);
  }

  console.log(`repoRoot     = ${repoRoot}`);
  console.log(`assetsRoot   = ${assetsRoot}`);
  console.log(`questionsRoot= ${questionsRoot}`);
  console.log(`locales      = ${locales.join(", ")}`);

  // 1) Build per-locale manifests
  for (const locale of locales) {
    const localeDir = path.join(questionsRoot, locale);
    const localeIndexPath = path.join(localeDir, "index.json");

    const meta = extractMetaFromExistingLocaleIndex(localeIndexPath, locale);

    const filesAbs = walkFilesSorted(localeDir).filter((abs) => path.basename(abs) !== "index.json");

    const entries = new Map(); // path -> { sha256 }
    for (const abs of filesAbs) {
      const rel = path.relative(assetsRoot, abs); // questions/<locale>/...
      const relPosix = toPosix(rel);
      const buf = readFileBytes(abs);
      entries.set(relPosix, { sha256: sha256(buf).toLowerCase() });
    }

    const sortedKeys = Array.from(entries.keys()).sort();
    const filesObj = {};
    for (const k of sortedKeys) filesObj[k] = entries.get(k);

    const out = {
      blueprintId: meta.blueprintId,
      bankVersion: meta.bankVersion,
      files: filesObj,
    };

    if (args.dryRun) {
      console.log(`[dry-run] would write ${toPosix(path.relative(repoRoot, localeIndexPath))} (${sortedKeys.length} entries)`);
    } else {
      writeJsonFile(localeIndexPath, out);
      console.log(`wrote ${toPosix(path.relative(repoRoot, localeIndexPath))} (${sortedKeys.length} entries)`);
    }
  }

  // 2) Build root summary index
  const generatedAt = pickGeneratedAt(rootIndexPath, args.generatedAt);

  const rootLocalesObj = {};
  for (const locale of locales) {
    const localeDir = path.join(questionsRoot, locale);
    const tasksDir = path.join(localeDir, "tasks");

    // Tasks list (A-1, A-2, ...)
    let tasks = [];
    if (isDir(tasksDir)) {
      tasks = listDirSorted(tasksDir)
        .filter((n) => n.endsWith(".json") || n.endsWith(".json.gz"))
        .map((n) => n.replace(/\.json(\.gz)?$/, ""));
    }

    // total: sum counts across task files (tolerant)
    let total = 0;
    if (isDir(tasksDir)) {
      for (const id of tasks) {
        const pJson = path.join(tasksDir, `${id}.json`);
        const pGz = path.join(tasksDir, `${id}.json.gz`);
        if (exists(pJson)) total += countQuestionsFromTaskJsonBytes(pJson, readFileBytes(pJson));
        else if (exists(pGz)) total += countQuestionsFromTaskJsonBytes(pGz, readFileBytes(pGz));
      }
    }
    if (total === 0 && tasks.length > 0) total = tasks.length; // fallback

    // sha256: hash of locale manifest index.json bytes (after we wrote it)
    const localeIndexPath = path.join(localeDir, "index.json");
    const localeIndexBytes = readFileBytes(localeIndexPath);
    const idxSha = sha256(localeIndexBytes).toLowerCase();

    rootLocalesObj[locale] = { total, tasks, sha256: idxSha };
  }

  // Deterministic order: rebuild rootLocalesObj with sorted keys
  const rootLocalesSorted = {};
  for (const k of Object.keys(rootLocalesObj).sort()) rootLocalesSorted[k] = rootLocalesObj[k];

  const rootIndex = {
    schema: "questions-index-v1",
    generatedAt,
    locales: rootLocalesSorted,
  };

  if (args.dryRun) {
    console.log(`[dry-run] would write ${toPosix(path.relative(repoRoot, rootIndexPath))}`);
  } else {
    writeJsonFile(rootIndexPath, rootIndex);
    console.log(`wrote ${toPosix(path.relative(repoRoot, rootIndexPath))}`);
  }

  console.log("OK");
}

main();

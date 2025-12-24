#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const zlib = require("zlib");

function sha256(buf) {
  return crypto.createHash("sha256").update(buf).digest("hex");
}
function exists(p) { try { fs.accessSync(p); return true; } catch { return false; } }
function isDir(p) { try { return fs.statSync(p).isDirectory(); } catch { return false; } }
function readJsonFile(p) { return JSON.parse(fs.readFileSync(p, "utf8")); }
function writeJsonFile(p, obj) { fs.writeFileSync(p, JSON.stringify(obj, null, 2) + "\n", "utf8"); }
function readBytes(p) { return fs.readFileSync(p); }
function listSorted(p) { return fs.readdirSync(p).slice().sort(); }
function walkFilesSorted(rootDir) {
  const out = [];
  function rec(dir) {
    for (const name of listSorted(dir)) {
      const abs = path.join(dir, name);
      const st = fs.statSync(abs);
      if (st.isDirectory()) rec(abs);
      else if (st.isFile()) out.push(abs);
    }
  }
  rec(rootDir);
  return out;
}
function toPosix(p) { return p.split(path.sep).join("/"); }

function findRepoRoot(startDir) {
  let cur = path.resolve(startDir);
  while (true) {
    if (
      exists(path.join(cur, "settings.gradle.kts")) ||
      exists(path.join(cur, "settings.gradle")) ||
      exists(path.join(cur, "gradlew")) ||
      exists(path.join(cur, ".git"))
    ) return cur;
    const parent = path.dirname(cur);
    if (parent === cur) return path.resolve(startDir);
    cur = parent;
  }
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
    else { console.error(`Unknown arg: ${a}`); process.exit(2); }
  }
  return args;
}

function pickGeneratedAt(rootIndexPath, mode) {
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
        if (prev && typeof prev.generatedAt === "string" && prev.generatedAt) return prev.generatedAt;
      } catch {}
    }
    const sde = process.env.SOURCE_DATE_EPOCH;
    if (sde && /^\d+$/.test(sde)) return new Date(Number(sde) * 1000).toISOString();
    return epochIso;
  }
  return mode; // explicit ISO
}

function extractMetaFromExistingLocaleIndex(localeIndexPath, locale) {
  if (!exists(localeIndexPath)) {
    throw new Error(`Missing ${localeIndexPath}. Cannot infer blueprintId/bankVersion for locale=${locale}.`);
  }
  const j = readJsonFile(localeIndexPath);

  // Manifest format:
  if (typeof j.blueprintId === "string" && typeof j.bankVersion === "string") {
    return { blueprintId: j.blueprintId, bankVersion: j.bankVersion };
  }

  // Hybrid fallback: locales[locale].blueprintId/bankVersion
  const loc = j && j.locales && j.locales[locale];
  if (loc && typeof loc.blueprintId === "string" && typeof loc.bankVersion === "string") {
    return { blueprintId: loc.blueprintId, bankVersion: loc.bankVersion };
  }

  throw new Error(`Cannot extract blueprintId/bankVersion from ${localeIndexPath} for locale=${locale}.`);
}

function countQuestionsFromTaskFile(filePath, rawBytes) {
  let bytes = rawBytes;
  if (filePath.endsWith(".gz")) bytes = zlib.gunzipSync(rawBytes);

  let j;
  try { j = JSON.parse(bytes.toString("utf8")); } catch { return 0; }

  if (Array.isArray(j)) return j.length;
  if (j && typeof j === "object") {
    if (Array.isArray(j.questions)) return j.questions.length;
    if (Array.isArray(j.items)) return j.items.length;
    if (Array.isArray(j.data)) return j.data.length;
  }
  return 0;
}

function selectTaskFile(tasksDir, id) {
  const pJson = path.join(tasksDir, `${id}.json`);
  const pGz = path.join(tasksDir, `${id}.json.gz`);
  if (exists(pJson)) return pJson;
  if (exists(pGz)) return pGz;
  return null;
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

  // Locales = directories under questions/
  const locales = listSorted(questionsRoot).filter((name) => isDir(path.join(questionsRoot, name)));
  if (locales.length === 0) {
    console.error(`No locale directories found under: ${questionsRoot}`);
    process.exit(1);
  }

  console.log(`repoRoot      = ${repoRoot}`);
  console.log(`assetsRoot    = ${assetsRoot}`);
  console.log(`questionsRoot = ${questionsRoot}`);
  console.log(`locales       = ${locales.join(", ")}`);

  // 1) Build per-locale manifests (already match schema branch #2)
  for (const locale of locales) {
    const localeDir = path.join(questionsRoot, locale);
    const localeIndexPath = path.join(localeDir, "index.json");
    const meta = extractMetaFromExistingLocaleIndex(localeIndexPath, locale);

    const filesAbs = walkFilesSorted(localeDir).filter((abs) => path.basename(abs) !== "index.json");

    const entries = new Map(); // relPosix -> {sha256}
    for (const abs of filesAbs) {
      const rel = path.relative(assetsRoot, abs); // questions/<locale>/...
      const relPosix = toPosix(rel);
      const buf = readBytes(abs);
      entries.set(relPosix, { sha256: sha256(buf).toLowerCase() });
    }

    const filesObj = {};
    for (const k of Array.from(entries.keys()).sort()) filesObj[k] = entries.get(k);

    const out = { blueprintId: meta.blueprintId, bankVersion: meta.bankVersion, files: filesObj };

    if (args.dryRun) {
      console.log(`[dry-run] would write ${toPosix(path.relative(repoRoot, localeIndexPath))} (${Object.keys(filesObj).length} entries)`);
    } else {
      writeJsonFile(localeIndexPath, out);
      console.log(`wrote ${toPosix(path.relative(repoRoot, localeIndexPath))} (${Object.keys(filesObj).length} entries)`);
    }
  }

  // 2) Build root summary index (schema branch #1) — IMPORTANT FIX:
  // locales.<locale>.tasks = object
  // locales.<locale>.sha256 = object
  const generatedAt = pickGeneratedAt(rootIndexPath, args.generatedAt);

  const localesOut = {};
  for (const locale of locales) {
    const localeDir = path.join(questionsRoot, locale);
    const tasksDir = path.join(localeDir, "tasks");

    // task ids from files (deterministic)
    let taskIds = [];
    if (isDir(tasksDir)) {
      taskIds = listSorted(tasksDir)
        .filter((n) => n.endsWith(".json") || n.endsWith(".json.gz"))
        .map((n) => n.replace(/\.json(\.gz)?$/, ""));
    }

    // total (tolerant)
    let total = 0;
    for (const id of taskIds) {
      const fp = selectTaskFile(tasksDir, id);
      if (!fp) continue;
      total += countQuestionsFromTaskFile(fp, readBytes(fp));
    }
    if (total === 0 && taskIds.length > 0) total = taskIds.length;

    // tasks object: taskId -> {sha256:"..."} (hash of the chosen task file bytes)
    const tasksObj = {};
    for (const id of taskIds) {
      const fp = selectTaskFile(tasksDir, id);
      if (!fp) continue;
      tasksObj[id] = { sha256: sha256(readBytes(fp)).toLowerCase() };
    }

    // sha256 object: {sha256:"..."} of the locale manifest index.json (after we wrote it)
    const localeIndexPath = path.join(localeDir, "index.json");
    const localeIndexBytes = readBytes(localeIndexPath);
    const localeIndexSha = sha256(localeIndexBytes).toLowerCase();

    localesOut[locale] = {
      total,
      tasks: tasksObj,
      sha256: { sha256: localeIndexSha },
    };
  }

  // deterministic locales order
  const localesSorted = {};
  for (const k of Object.keys(localesOut).sort()) localesSorted[k] = localesOut[k];

  const rootIndex = {
    schema: "questions-index-v1",
    generatedAt,
    locales: localesSorted,
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

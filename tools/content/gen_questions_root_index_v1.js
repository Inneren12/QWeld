#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const zlib = require("zlib");

function sha256(buf) {
  return crypto.createHash("sha256").update(buf).digest("hex").toLowerCase();
}
function exists(p) { try { fs.accessSync(p); return true; } catch { return false; } }
function isDir(p) { try { return fs.statSync(p).isDirectory(); } catch { return false; } }
function listSorted(p) { return fs.readdirSync(p).slice().sort(); }
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

function readJson(p) { return JSON.parse(fs.readFileSync(p, "utf8")); }
function writeJson(p, obj) { fs.writeFileSync(p, JSON.stringify(obj, null, 2) + "\n", "utf8"); }

function stripJsonExt(name) { return name.replace(/\.json(\.gz)?$/i, ""); }

function gunzipIfNeeded(absPath, bytes) {
  return absPath.endsWith(".gz") ? zlib.gunzipSync(bytes) : bytes;
}

function countQuestions(absPath) {
  const raw = fs.readFileSync(absPath);
  const bytes = gunzipIfNeeded(absPath, raw);
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

function pickGeneratedAt(rootIndexPath) {
  const epochIso = new Date(0).toISOString();
  if (exists(rootIndexPath)) {
    try {
      const prev = readJson(rootIndexPath);
      if (typeof prev.generatedAt === "string" && prev.generatedAt) return prev.generatedAt;
    } catch {}
  }
  const sde = process.env.SOURCE_DATE_EPOCH;
  if (sde && /^\d+$/.test(sde)) return new Date(Number(sde) * 1000).toISOString();
  return epochIso;
}

function sortObjectKeys(obj) {
  const out = {};
  for (const k of Object.keys(obj).sort()) out[k] = obj[k];
  return out;
}

function findBankFile(localeDir) {
  // Try common names; adapt if yours differs.
  const candidates = [
    "bank.v1.json",
    "bank.json",
    "bank.v1.json.gz",
    "bank.json.gz",
  ];
  for (const c of candidates) {
    const abs = path.join(localeDir, c);
    if (exists(abs)) return abs;
  }
  // fallback: any bank*.json*
  const all = listSorted(localeDir).filter(n => /^bank.*\.json(\.gz)?$/i.test(n));
  if (all.length > 0) return path.join(localeDir, all[0]);
  return null;
}

function main() {
  const repoRoot = findRepoRoot(process.cwd());
  const assetsRoot = path.join(repoRoot, "app-android", "src", "main", "assets");
  const questionsRoot = path.join(assetsRoot, "questions");
  const rootIndexPath = path.join(questionsRoot, "index.json");

  if (!isDir(questionsRoot)) {
    console.error(`questions root not found: ${questionsRoot}`);
    process.exit(1);
  }

  const locales = listSorted(questionsRoot).filter(n => isDir(path.join(questionsRoot, n)));
  if (locales.length === 0) {
    console.error(`no locale dirs under: ${questionsRoot}`);
    process.exit(1);
  }

  const generatedAt = pickGeneratedAt(rootIndexPath);
  const localesOut = {};

  for (const locale of locales) {
    const localeDir = path.join(questionsRoot, locale);
    const tasksDir = path.join(localeDir, "tasks");

    // tasks: taskId -> integer count
    const tasksMap = {};
    let total = 0;

    if (isDir(tasksDir)) {
      const taskFiles = listSorted(tasksDir).filter(n => n.endsWith(".json") || n.endsWith(".json.gz"));
      for (const fn of taskFiles) {
        const id = stripJsonExt(fn);
        const abs = path.join(tasksDir, fn);
        const c = countQuestions(abs);
        tasksMap[id] = c;
        total += c;
      }
      if (total === 0 && taskFiles.length > 0) {
        // fallback if counting fails
        total = taskFiles.length;
        for (const fn of taskFiles) {
          const id = stripJsonExt(fn);
          if (tasksMap[id] === 0) tasksMap[id] = 1;
        }
      }
    }

    // sha256.bank: hash of bank file bytes
    const bankAbs = findBankFile(localeDir);
    if (!bankAbs) {
      console.error(`bank file not found for locale=${locale} under ${localeDir}`);
      process.exit(1);
    }
    const bankHash = sha256(fs.readFileSync(bankAbs));

    // sha256.tasks: deterministic aggregate of per-task file hashes (not counts)
    // We hash the actual task JSON bytes so it changes when content changes.
    const taskHashes = [];
    if (isDir(tasksDir)) {
      const taskFiles = listSorted(tasksDir).filter(n => n.endsWith(".json") || n.endsWith(".json.gz"));
      for (const fn of taskFiles) {
        const id = stripJsonExt(fn);
        const abs = path.join(tasksDir, fn);
        const h = sha256(fs.readFileSync(abs));
        taskHashes.push(`${id}:${h}\n`);
      }
    }
    const tasksAggHash = sha256(Buffer.from(taskHashes.join(""), "utf8"));

    localesOut[locale] = {
      total,
      tasks: sortObjectKeys(tasksMap),
      sha256: { bank: bankHash, tasks: tasksAggHash },
    };
  }

  const rootIndex = {
    schema: "questions-index-v1",
    generatedAt,
    locales: sortObjectKeys(localesOut),
  };

  writeJson(rootIndexPath, rootIndex);
  console.log("OK: rebuilt root questions/index.json");
}

main();

// Node 18+
import { promises as fs } from "fs";
import path from "path";

const MAP = new Map([
  ["English", "en"],
  ["EN", "en"],
  ["en", "en"],
  ["Russian", "ru"],
  ["RU", "ru"],
  ["ru", "ru"],
]);

const DIST = "dist/questions";
const ASSETS = "app-android/src/main/assets/questions";
const TASKS = ["A-1","A-2","A-3","A-4","A-5","B-6","B-7","C-8","C-9","C-10","C-11","D-12","D-13","D-14","D-15"];

async function copyFile(src, dst) {
  await fs.mkdir(path.dirname(dst), { recursive: true });
  await fs.copyFile(src, dst);
}

async function run() {
  for (const entry of await fs.readdir(DIST, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const fromLocale = entry.name;
    const toLocale = MAP.get(fromLocale);
    if (!toLocale) {
      console.warn(`[sync] skip unknown locale folder: ${fromLocale}`);
      continue;
    }
    const distLocaleDir = path.join(DIST, fromLocale);
    const assetsLocaleDir = path.join(ASSETS, toLocale);

    // bank.v1.json (если есть)
    const bank = path.join(distLocaleDir, "bank.v1.json");
    try { await fs.access(bank); await copyFile(bank, path.join(assetsLocaleDir, "bank.v1.json")); }
    catch { /* ok, нет банка — пропускаем */ }

    // per-task
    for (const t of TASKS) {
      const src = path.join(distLocaleDir, "tasks", `${t}.json`);
      try {
        await fs.access(src);
        const dst = path.join(assetsLocaleDir, "tasks", `${t}.json`);
        await copyFile(src, dst);
        console.log(`[sync] ${fromLocale}/${t} -> assets/${toLocale}/tasks/${t}.json`);
      } catch { /* конкретного task может не быть — пропускаем */ }
    }
  }
  console.log("[sync] done.");
}

run().catch(e => { console.error(e); process.exit(1); });

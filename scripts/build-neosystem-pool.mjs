// Node 18+
import { promises as fs } from "fs";
import path from "path";

const ARG = Object.fromEntries(process.argv.slice(2).map(s => {
  const [k, ...rest] = s.split("="); return [k.replace(/^--/,""), rest.join("=") || true];
}));

const SRC   = ARG.src   || "Neosystem/questions/en";      // исходники (рекурсивно)
const LOCALE= ARG.locale|| "en";                           // en|ru
const BP    = ARG.bp    || "app-android/src/main/assets/blueprints/welder_ip_sk_202404.json";
const OUT   = `dist/questions/${LOCALE}`;
const TASK_OUT = path.join(OUT, "tasks");

const normTask = (t) => {
  if (!t) return t;
  const m = String(t).trim().match(/^([A-D])\s*-?\s*(\d{1,2})$/i);
  return m ? `${m[1].toUpperCase()}-${m[2]}` : t;
};

async function readJson(p){ return JSON.parse(await fs.readFile(p, "utf-8")); }

async function readBlueprintTasks() {
  try {
    const bp = await readJson(BP);
    const tasks = [];
    for (const b of (bp.blocks||[])) for (const t of (b.tasks||[])) tasks.push(normTask(t.id));
    const unique = [...new Set(tasks)];
    if (unique.length !== tasks.length) console.warn("[warn] duplicate taskIds in blueprint");
    return unique;
  } catch (e) {
    console.warn(`[warn] blueprint read failed (${BP}), fallback to canonical A-1..D-15`);
    return ["A-1","A-2","A-3","A-4","A-5","B-6","B-7","C-8","C-9","C-10","C-11","D-12","D-13","D-14","D-15"];
  }
}

async function listFiles(dir) {
  const out = [];
  async function walk(d) {
    for (const ent of await fs.readdir(d, { withFileTypes: true })) {
      const p = path.join(d, ent.name);
      if (ent.isDirectory()) await walk(p);
      else if (ent.isFile() && p.endsWith(".json")) out.push(p);
    }
  }
  await walk(dir);
  return out;
}

async function main() {
  const TASKS = await readBlueprintTasks();
  const files = await listFiles(SRC);
  const all = [];
  for (const f of files) {
    try {
      const q = await readJson(f);
      if (q && q.locale === LOCALE) {
        q.taskId = normTask(q.taskId);
        all.push(q);
      }
    } catch (_) {}
  }
  const by = new Map(TASKS.map(t => [t, []]));
  for (const q of all) if (by.has(q.taskId)) by.get(q.taskId).push(q);

  // mkdirs
  await fs.mkdir(TASK_OUT, { recursive: true });

  // per-task bundles
  for (const t of TASKS) {
    const arr = (by.get(t)||[]).sort((a,b)=>String(a.id).localeCompare(String(b.id)));
    await fs.writeFile(path.join(TASK_OUT, `${t}.json`), JSON.stringify(arr, null, 2));
  }

  // unified bank
  const bank = TASKS.flatMap(t => by.get(t) || []);
  await fs.mkdir(OUT, { recursive: true });
  await fs.writeFile(path.join(OUT, "bank.v1.json"), JSON.stringify(bank, null, 2));

  // summary
  const counts = TASKS.map(t => `${t}=${(by.get(t)||[]).length}`).join("  ");
  console.log(`[pool] locale=${LOCALE} src=${SRC}`);
  console.log(`[pool] tasks: ${counts}`);
  console.log(`[pool] bank: ${bank.length} -> ${path.join(OUT,"bank.v1.json")}`);
  console.log(`[pool] per-task dir: ${TASK_OUT}`);
}

main().catch(e => { console.error(e); process.exit(1); });

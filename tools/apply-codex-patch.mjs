#!/usr/bin/env node
import fs from 'fs';
import path from 'path';

const readAll = async () => {
  if (process.argv[2]) return fs.readFileSync(process.argv[2], 'utf8');
  return await new Promise(res => {
    let s = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', c => (s += c));
    process.stdin.on('end', () => res(s));
  });
};

const txt = await readAll();
const lines = txt.split(/\r?\n/);
let cur = null; // {op, file, buf}
const flush = () => {
  if (!cur) return;
  const { op, file, buf } = cur;
  if (op === 'update' || op === 'add') {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, buf.replace(/\n$/, ''), 'utf8');
    console.log(`[write] ${file}`);
  } else if (op === 'delete') {
    if (fs.existsSync(file)) {
      fs.unlinkSync(file);
      console.log(`[delete] ${file}`);
    } else {
      console.log(`[skip] not found: ${file}`);
    }
  }
  cur = null;
};

for (let i = 0; i < lines.length; i++) {
  const L = lines[i];

  if (/^=== END PATCH/.test(L)) {
    flush();
    continue;
  }
  let m;
  if ((m = L.match(/^\*\*\* (Add|Update|Delete) File:\s+(.*)$/))) {
    flush();
    const op = m[1].toLowerCase();
    const file = m[2].trim();
    cur = { op: op === 'delete' ? 'delete' : op, file, buf: '' };
    continue;
  }
  // пропускаем служебные строки
  if (/^\+\+\+ REPLACE ENTIRE FILE/.test(L)) continue;
  if (/^=== BEGIN PATCH/.test(L)) continue;
  if (/^@@/.test(L)) continue;

  if (cur && (cur.op === 'add' || cur.op === 'update')) cur.buf += L + '\n';
}

flush();

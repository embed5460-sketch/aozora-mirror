// 构建合规图书索引：
//  1. 解析青空文库官方元数据 CSV
//  2. 双重著作権フラグ过滤（作品=なし AND 人物=なし）→ 仅保留公版
//  3. 将 テキストファイルURL 映射到本地 txt 文件路径，校验存在性
//  4. 产出 books-index.json + 统计报告
//
// 用法: node pipeline/src/build-index.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..', '..');
const CSV = path.join(ROOT, 'pipeline', 'data', 'list_person_all_extended_utf8.csv');
const CARDS = path.join(ROOT, 'Tools', 'aozorabunko_text-master', 'cards');
const OUT_DIR = path.join(ROOT, 'pipeline', 'out');

// --- 极简 CSV 解析（支持引号内逗号/换行） ---
function parseCSV(text) {
  const rows = [];
  let row = [], field = '', inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i++; }
        else inQuotes = false;
      } else field += c;
    } else {
      if (c === '"') inQuotes = true;
      else if (c === ',') { row.push(field); field = ''; }
      else if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; }
      else if (c === '\r') { /* skip */ }
      else field += c;
    }
  }
  if (field.length || row.length) { row.push(field); rows.push(row); }
  return rows;
}

// テキストファイルURL → 本地 txt 路径
// 例: https://www.aozora.gr.jp/cards/001257/files/59898_ruby_70679.zip
//   → cards/001257/files/59898_ruby_70679/59898_ruby_70679.txt
function urlToLocalPath(url) {
  const m = url.match(/cards\/(\d+)\/files\/([^/]+)\.zip$/);
  if (!m) return null;
  const [, personId, stem] = m;
  return path.join(CARDS, personId, 'files', stem, `${stem}.txt`);
}

function main() {
  const raw = fs.readFileSync(CSV, 'utf-8').replace(/^﻿/, '');
  const rows = parseCSV(raw);
  const header = rows[0];
  const idx = Object.fromEntries(header.map((h, i) => [h.replace(/^﻿/, ''), i]));

  const col = (row, name) => row[idx[name]] ?? '';

  const stats = {
    totalRows: 0,
    publicDomain: 0,        // 双フラグ なし
    workCopyright: 0,       // 作品著作権あり
    personCopyright: 0,     // 人物著作権あり
    pdFileExists: 0,        // 公版且本地文件存在
    pdFileMissing: 0,       // 公版但本地缺文件
    notTxtUrl: 0,           // URL 解析失败
  };

  const books = [];
  const missing = [];

  for (let r = 1; r < rows.length; r++) {
    const row = rows[r];
    if (!row || row.length < 2) continue;
    stats.totalRows++;

    const workFlag = col(row, '作品著作権フラグ');
    const personFlag = col(row, '人物著作権フラグ');
    if (workFlag === 'あり') stats.workCopyright++;
    if (personFlag === 'あり') stats.personCopyright++;

    const isPD = workFlag === 'なし' && personFlag === 'なし';
    if (!isPD) continue;
    stats.publicDomain++;

    const url = col(row, 'テキストファイルURL');
    const local = url ? urlToLocalPath(url) : null;
    if (!local) { stats.notTxtUrl++; continue; }

    const exists = fs.existsSync(local);
    if (!exists) {
      stats.pdFileMissing++;
      missing.push({ workId: col(row, '作品ID'), title: col(row, '作品名'), url });
      continue;
    }
    stats.pdFileExists++;

    const sei = col(row, '姓'), mei = col(row, '名');
    books.push({
      workId: col(row, '作品ID'),
      title: col(row, '作品名'),
      titleKana: col(row, '作品名読み'),
      subtitle: col(row, '副題'),
      author: `${sei}${mei}`.trim(),
      authorKana: `${col(row, '姓読み')}${col(row, '名読み')}`.trim(),
      authorRoman: `${col(row, '名ローマ字')} ${col(row, '姓ローマ字')}`.trim(),
      personId: col(row, '人物ID'),
      birth: col(row, '生年月日'),
      death: col(row, '没年月日'),
      style: col(row, '文字遣い種別'),       // 新字新仮名 / 旧字旧仮名 等
      category: col(row, '分類番号'),         // NDC
      cardUrl: col(row, '図書カードURL'),
      // 本地相对路径（供后续解析阶段使用）
      file: path.relative(ROOT, local).replace(/\\/g, '/'),
      encoding: col(row, 'テキストファイル符号化方式'),
    });
  }

  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(path.join(OUT_DIR, 'books-index.json'), JSON.stringify(books, null, 2));
  fs.writeFileSync(path.join(OUT_DIR, 'missing-files.json'), JSON.stringify(missing.slice(0, 200), null, 2));

  // 按作者聚合 top
  const byAuthor = {};
  for (const b of books) byAuthor[b.author] = (byAuthor[b.author] || 0) + 1;
  const topAuthors = Object.entries(byAuthor).sort((a, b) => b[1] - a[1]).slice(0, 20);

  // 按文字遣い种别
  const byStyle = {};
  for (const b of books) byStyle[b.style] = (byStyle[b.style] || 0) + 1;

  console.log('========== 合规索引统计 ==========');
  console.log('CSV 总作品行数      :', stats.totalRows);
  console.log('作品著作権「あり」  :', stats.workCopyright);
  console.log('人物著作権「あり」  :', stats.personCopyright);
  console.log('双フラグ公版(なし)  :', stats.publicDomain);
  console.log('  └ 本地文件存在    :', stats.pdFileExists, '  <-- 实际可发行书目');
  console.log('  └ 本地文件缺失    :', stats.pdFileMissing);
  console.log('  └ URL无法映射     :', stats.notTxtUrl);
  console.log('文字遣い种别分布    :', JSON.stringify(byStyle));
  console.log('唯一作者数          :', Object.keys(byAuthor).length);
  console.log('========== Top 20 作者(作品数) ==========');
  for (const [a, n] of topAuthors) console.log(`  ${n}\t${a}`);
  console.log('\n输出: pipeline/out/books-index.json');
}

main();

// 抽样验证：从合规索引中取样，解析为结构化 JSON，并生成 HTML 预览验证 ruby 渲染
// 用法: node pipeline/src/build-sample.mjs [数量,默认100]

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseAozora } from './aozora-parser.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..', '..');
const OUT_DIR = path.join(ROOT, 'pipeline', 'out');
const N = parseInt(process.argv[2] || '100', 10);

const index = JSON.parse(fs.readFileSync(path.join(OUT_DIR, 'books-index.json'), 'utf-8'));

// 名家优先 + 多样化抽样
const FAMOUS = ['夏目漱石', '芥川竜之介', '太宰治', '宮沢賢治', '森鴎外', '坂口安吾',
  '泉鏡花', '中島敦', '梶井基次郎', '樋口一葉', '宮本百合子', '小川未明'];
const picked = [];
const seen = new Set();
const add = (b) => { if (b && !seen.has(b.file)) { seen.add(b.file); picked.push(b); } };

for (const name of FAMOUS) {
  const matches = index.filter(b => b.author === name).slice(0, 4);
  matches.forEach(add);
}
// 补足：均匀步长扫描整库（覆盖各种文字遣い）
const step = Math.max(1, Math.floor(index.length / (N * 2)));
for (let i = 0; picked.length < N && i < index.length; i += step) add(index[i]);

const sample = picked.slice(0, N);

const parsed = [];
const failures = [];
let totalRuby = 0, totalGaiji = 0, totalNote = 0, emptyBody = 0;

for (const b of sample) {
  try {
    const buf = fs.readFileSync(path.join(ROOT, b.file));
    const result = parseAozora(buf);
    totalRuby += result.stats.ruby;
    totalGaiji += result.stats.gaiji;
    totalNote += result.stats.note;
    if (result.stats.lines === 0) emptyBody++;
    parsed.push({ book: b, result });
  } catch (e) {
    failures.push({ file: b.file, error: String(e) });
  }
}

fs.mkdirSync(path.join(OUT_DIR, 'parsed'), { recursive: true });
// 存前 5 本完整 JSON 供检视
for (const p of parsed.slice(0, 5)) {
  const fn = p.book.workId + '_' + p.book.title.replace(/[\\/:*?"<>|]/g, '_').slice(0, 20) + '.json';
  fs.writeFileSync(path.join(OUT_DIR, 'parsed', fn), JSON.stringify(p.result, null, 2));
}

// --- HTML 预览 ---
function runsToHtml(runs) {
  return runs.map(r => {
    if (r.blank) return '';
    if (r.t != null) return esc(r.t);
    if (r.base != null) return `<ruby>${esc(r.base)}<rt>${esc(r.ruby)}</rt></ruby>`;
    if (r.gaiji != null) return `<span class="gaiji" title="${esc(r.note)}">${r.gaiji}</span>`;
    if (r.note != null) return `<span class="note" title="${esc(r.note)}">［注］</span>`;
    return '';
  }).join('');
}
function esc(s) { return String(s).replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c])); }

let html = `<!doctype html><meta charset="utf-8"><title>青空解析预览</title>
<style>
body{font-family:"Yu Mincho","Hiragino Mincho ProN",serif;max-width:780px;margin:40px auto;line-height:2.1;color:#222;padding:0 20px}
h1{font-size:20px;border-bottom:2px solid #333;padding-bottom:8px}
.book{margin:60px 0;padding-bottom:40px;border-bottom:1px dashed #ccc}
.meta{color:#666;font-size:13px;margin-bottom:20px}
.body p{margin:0}
rt{font-size:.5em;color:#0a6}
.gaiji{color:#c00}
.note{color:#39c;font-size:.7em;vertical-align:super;cursor:help}
</style>
<h1>青空格式解析预览（前 20 本）</h1>`;

for (const p of parsed.slice(0, 20)) {
  const blocks = p.result.body.slice(0, 18); // 每本只预览开头
  html += `<div class="book"><div class="meta"><b>${esc(p.result.meta.title)}</b> ／ ${esc(p.result.meta.author)}
   ｜ ruby:${p.result.stats.ruby} note:${p.result.stats.note} gaiji:${p.result.stats.gaiji} ｜ ${esc(p.book.style)}</div><div class="body">`;
  for (const blk of blocks) {
    if (blk.blank) { html += '<p>&nbsp;</p>'; continue; }
    html += `<p>${runsToHtml(blk.runs)}</p>`;
  }
  html += `</div></div>`;
}
fs.writeFileSync(path.join(OUT_DIR, 'preview.html'), html);

// --- 报告 ---
console.log('========== 抽样解析报告 ==========');
console.log('样本数            :', sample.length);
console.log('解析成功          :', parsed.length);
console.log('解析失败          :', failures.length);
console.log('正文为空(疑似)    :', emptyBody);
console.log('ruby 总数         :', totalRuby, `(均 ${(totalRuby / parsed.length).toFixed(0)}/本)`);
console.log('注释 总数         :', totalNote);
console.log('外字 总数         :', totalGaiji);
if (failures.length) {
  console.log('--- 失败样本 ---');
  failures.slice(0, 10).forEach(f => console.log(' ', f.file, f.error));
}
console.log('\n输出:');
console.log('  pipeline/out/preview.html   <- 浏览器打开验证 ruby 渲染');
console.log('  pipeline/out/parsed/*.json  <- 前5本完整结构');

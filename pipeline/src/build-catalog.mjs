// 生产构建器：按数据契约 v1 产出 Cloudflare 可部署的 cdn/ 目录树
//   cdn/meta.json, cdn/catalog/{authors,books}.json, cdn/books/{id}.json
//
// 用法:
//   node pipeline/src/build-catalog.mjs            精选首发(curated), 全部
//   node pipeline/src/build-catalog.mjs --full     全量 18,081 本
//   node pipeline/src/build-catalog.mjs --limit 20 仅前 20 本(快速抽样验证)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseAozora } from './aozora-parser.mjs';
import { isCurated } from './curation.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..', '..');
const OUT_DIR = path.join(ROOT, 'pipeline', 'out');
const CDN = path.join(ROOT, 'cdn');

const argv = process.argv.slice(2);
const FULL = argv.includes('--full');
const limitIdx = argv.indexOf('--limit');
const LIMIT = limitIdx !== -1 ? parseInt(argv[limitIdx + 1], 10) : Infinity;

// --- 注记分类（语义增强） ---
const RE_HEADING = /は(大|中|小)見出し/;
const HEADING_LEVEL = { 大: 1, 中: 2, 小: 3 };
// 纯排版指令：客户端自行重排，剔除
const RE_LAYOUT = /字下げ|字上げ|字詰め|改丁|改ページ|改段|左右中央|中央揃え|地付き|地から|行アキ|行空き|横組|縦中横|ページの|右寄せ|左寄せ|割り注|底本では/;
const RE_BOUTEN = /に傍点|に白ゴマ傍点|に丸傍点|に二重傍点|傍線/;

function isStructuralNote(s) {
  return RE_HEADING.test(s) || RE_LAYOUT.test(s) || RE_BOUTEN.test(s);
}

// 解析结果 → 契约 blocks
function toBlocks(parsed) {
  const blocks = [];
  for (const blk of parsed.body) {
    if (blk.blank) {
      // 折叠连续空行
      if (blocks.length && blocks[blocks.length - 1].k === 'blank') continue;
      blocks.push({ k: 'blank' });
      continue;
    }
    // 检测标题级别
    let headingLevel = 0;
    for (const r of blk.runs) {
      if (r.note) {
        const m = r.note.match(RE_HEADING);
        if (m) { headingLevel = HEADING_LEVEL[m[1]]; break; }
      }
    }
    // 映射并过滤 runs
    const runs = [];
    for (const r of blk.runs) {
      if (r.t != null) runs.push({ t: r.t });
      else if (r.base != null) runs.push({ b: r.base, f: r.ruby });
      else if (r.gaiji != null) runs.push({ g: r.gaiji, d: r.note });
      else if (r.note != null) {
        if (!isStructuralNote(r.note)) runs.push({ n: r.note }); // 仅保留非结构性注记
      }
    }
    if (runs.length === 0 && !headingLevel) continue; // 全是排版指令 → 整块丢弃
    blocks.push(headingLevel ? { k: 'h', level: headingLevel, r: runs } : { k: 'p', r: runs });
  }
  // 去除首尾多余空行
  while (blocks.length && blocks[0].k === 'blank') blocks.shift();
  while (blocks.length && blocks[blocks.length - 1].k === 'blank') blocks.pop();
  return blocks;
}

function main() {
  const index = JSON.parse(fs.readFileSync(path.join(OUT_DIR, 'books-index.json'), 'utf-8'));
  // 按 workId 去重：青空 CSV 为人物中心结构（作品×人物各一行），同一作品会重复出现
  // （如译作同时挂原作者与译者）。精选过滤已按行剔除非名家归属，故保留首个出现即可。
  const filtered = (FULL ? index : index.filter(isCurated));
  const seenWork = new Set();
  const selected = filtered.filter(b => {
    if (seenWork.has(b.workId)) return false;
    seenWork.add(b.workId);
    return true;
  }).slice(0, LIMIT);
  const dupRemoved = filtered.length - seenWork.size;

  // 清理并重建 cdn/
  fs.rmSync(CDN, { recursive: true, force: true });
  fs.mkdirSync(path.join(CDN, 'catalog'), { recursive: true });
  fs.mkdirSync(path.join(CDN, 'books'), { recursive: true });

  const booksCatalog = [];
  const authorsMap = new Map();
  let totalBytes = 0, failed = 0, totalBlocks = 0;

  for (const b of selected) {
    let parsed;
    try { parsed = parseAozora(fs.readFileSync(path.join(ROOT, b.file))); }
    catch (e) { failed++; continue; }

    const blocks = toBlocks(parsed);
    totalBlocks += blocks.length;
    const doc = { id: b.workId, title: b.title, author: b.author, authorId: b.personId, blocks };
    const json = JSON.stringify(doc);
    totalBytes += Buffer.byteLength(json);
    fs.writeFileSync(path.join(CDN, 'books', `${b.workId}.json`), json);

    booksCatalog.push({ id: b.workId, t: b.title, tk: b.titleKana, a: b.personId, c: b.category });

    if (!authorsMap.has(b.personId)) {
      authorsMap.set(b.personId, {
        id: b.personId, name: b.author, kana: b.authorKana,
        roman: b.authorRoman, birth: b.birth, death: b.death, count: 0,
      });
    }
    authorsMap.get(b.personId).count++;
  }

  const authors = [...authorsMap.values()].sort((a, b) => b.count - a.count);
  booksCatalog.sort((a, b) => (a.tk || '').localeCompare(b.tk || '', 'ja'));

  const meta = {
    version: 1,
    generatedAt: '',                       // 由发布流程填充（脚本内禁用时钟）
    edition: FULL ? 'full' : 'curated',
    bookCount: booksCatalog.length,
    authorCount: authors.length,
    schema: 'v1',
  };

  fs.writeFileSync(path.join(CDN, 'meta.json'), JSON.stringify(meta, null, 2));
  fs.writeFileSync(path.join(CDN, 'catalog', 'authors.json'), JSON.stringify(authors));
  fs.writeFileSync(path.join(CDN, 'catalog', 'books.json'), JSON.stringify(booksCatalog));

  const catalogBytes = Buffer.byteLength(JSON.stringify(authors)) + Buffer.byteLength(JSON.stringify(booksCatalog));

  console.log('========== 生产构建报告 ==========');
  console.log('edition           :', meta.edition);
  console.log('入选作品(去重后)    :', selected.length, ' (去除重复workId:', dupRemoved + ')');
  console.log('成功生成           :', booksCatalog.length, ' 失败:', failed);
  console.log('作者数             :', authors.length);
  console.log('正文总段落数        :', totalBlocks.toLocaleString());
  console.log('books/ 总体积       :', (totalBytes / 1048576).toFixed(1), 'MB (未压缩)');
  console.log('单本均大小          :', (totalBytes / booksCatalog.length / 1024).toFixed(1), 'KB');
  console.log('catalog/ 体积       :', (catalogBytes / 1024).toFixed(0), 'KB (随包内置, 未压缩)');
  console.log('\n输出目录: cdn/  (Cloudflare Pages 站点B 根)');
}

main();

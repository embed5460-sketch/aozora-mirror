// 青空文库格式解析器
// 输入: SHIFT_JIS 编码的青空 .txt 文件 buffer
// 输出: { meta, body: [block...] }，block 为段落，含结构化 runs（普通文本 / ruby / note）
//
// 处理的标记:
//   《かな》          ルビ（振假名），附着到前方同字类的最长连续串
//   ｜...《かな》      ｜显式指定 ruby 基串起点
//   ［＃...］         入力者注：排版指令 / 傍点 / 外字说明等
//   ※［＃...］        外字：优先用 JIS X 0213 / U+ 还原为真字符，否则占位符 〓 + 说明

import { resolveGaiji } from './gaiji.mjs';

const DELIM = /^[-－―=]{10,}$/; // 凡例/底本分隔线（连续短横）

// 字符分类（用于 ruby 自动附着）
function charClass(ch) {
  const c = ch.codePointAt(0);
  // CJK 统一汉字 + 扩展 + 兼容 + 部分符号(々〆〇ヶ)
  if (
    (c >= 0x4e00 && c <= 0x9fff) ||
    (c >= 0x3400 && c <= 0x4dbf) ||
    (c >= 0xf900 && c <= 0xfaff) ||
    ch === '々' || ch === '〆' || ch === '〇' || ch === '〻'
  ) return 'kanji';
  if (c >= 0x3040 && c <= 0x309f) return 'hira';
  if ((c >= 0x30a0 && c <= 0x30ff) || (c >= 0xff66 && c <= 0xff9f)) return 'kata';
  if (
    (c >= 0x30 && c <= 0x39) || (c >= 0x41 && c <= 0x5a) || (c >= 0x61 && c <= 0x7a) ||
    (c >= 0xff10 && c <= 0xff19) || (c >= 0xff21 && c <= 0xff3a) || (c >= 0xff41 && c <= 0xff5a)
  ) return 'alnum';
  return 'other';
}

// 从基串文本(末尾)向前取与最后一个字符同类的最长连续串作为 ruby 基
function splitRubyBase(text) {
  const chars = [...text];
  if (chars.length === 0) return ['', ''];
  const cls = charClass(chars[chars.length - 1]);
  let i = chars.length - 1;
  while (i >= 0 && charClass(chars[i]) === cls) i--;
  const base = chars.slice(i + 1).join('');
  const prefix = chars.slice(0, i + 1).join('');
  return [prefix, base];
}

// 解析一行正文为 runs
function parseLine(line, stats) {
  const runs = [];
  let buf = '';                 // 待处理的普通文本累积
  let explicitBase = null;      // ｜ 标记的显式基串起点（buf 中的下标）

  const flushText = () => {
    if (buf) { runs.push({ t: buf }); buf = ''; }
    explicitBase = null;
  };

  for (let i = 0; i < line.length; i++) {
    const ch = line[i];

    // 外字 / 注释 ［＃...］（可能前置 ※）
    if (ch === '※' && line[i + 1] === '［' && line[i + 2] === '＃') {
      const end = line.indexOf('］', i);
      if (end !== -1) {
        const note = line.slice(i + 3, end); // 去掉 ※［＃
        const resolved = resolveGaiji(note);
        if (resolved !== null) {
          buf += resolved;                 // 还原成功 → 当作普通文本并入
          stats.gaijiResolved++;
        } else {
          flushText();
          runs.push({ gaiji: '〓', note }); // 无法还原 → 保留占位符 + 说明
          stats.gaiji++;
        }
        i = end;
        continue;
      }
    }
    if (ch === '［' && line[i + 1] === '＃') {
      const end = line.indexOf('］', i);
      if (end !== -1) {
        const note = line.slice(i + 2, end);
        flushText();
        runs.push({ note });
        stats.note++;
        i = end;
        continue;
      }
    }

    // ｜ ruby 起点标记
    if (ch === '｜') {
      explicitBase = buf.length;
      continue;
    }

    // 《ルビ》
    if (ch === '《') {
      const end = line.indexOf('》', i);
      if (end !== -1) {
        const ruby = line.slice(i + 1, end);
        let base;
        if (explicitBase !== null) {
          base = buf.slice(explicitBase);
          buf = buf.slice(0, explicitBase);
        } else {
          const [prefix, autoBase] = splitRubyBase(buf);
          base = autoBase;
          buf = prefix;
        }
        flushText(); // 推入 ruby 之前的剩余普通文本
        runs.push({ base, ruby });
        stats.ruby++;
        explicitBase = null;
        i = end;
        continue;
      }
    }

    buf += ch;
  }
  flushText();
  return runs;
}

// 主解析入口
export function parseAozora(buf) {
  const text = new TextDecoder('shift_jis').decode(buf);
  const lines = text.split(/\r?\n/);

  // --- 1. 头部元信息 ---
  // 凡例块(被两条分隔线包裹)仅在文件开头窗口内出现；正文中的分隔线不算凡例。
  const HEAD_WINDOW = 40;
  const meta = { title: '', author: '', extraHead: [] };

  // 凡例存在性：在开头窗口内寻找第一条分隔线
  let firstDelim = -1;
  for (let k = 0; k < Math.min(lines.length, HEAD_WINDOW); k++) {
    if (DELIM.test(lines[k].trim())) { firstDelim = k; break; }
  }

  let bodyStart;
  let hasReimei = false;
  if (firstDelim !== -1) {
    // 有凡例：头部=分隔线之前的非空行，随后跳过凡例块
    const headLines = [];
    for (let k = 0; k < firstDelim; k++) if (lines[k].trim()) headLines.push(lines[k].trim());
    meta.title = headLines[0] || '';
    meta.author = headLines[1] || '';
    meta.extraHead = headLines.slice(2);
    hasReimei = true;
    let idx = firstDelim + 1;                                  // 越过第一条分隔线
    while (idx < lines.length && !DELIM.test(lines[idx].trim())) idx++;
    if (idx < lines.length) idx++;                             // 越过第二条分隔线
    bodyStart = idx;
  } else {
    // 无凡例：标题=首个非空行，作者=次个非空行，正文从作者行之后开始
    let idx = 0;
    const skipBlank = () => { while (idx < lines.length && !lines[idx].trim()) idx++; };
    skipBlank(); meta.title = (lines[idx] || '').trim(); idx++;
    skipBlank(); meta.author = (lines[idx] || '').trim(); idx++;
    bodyStart = idx;
  }

  // --- 2. 尾部底本块：从末尾找最后一段以 "底本：" 起始的区域 ---
  let bodyEnd = lines.length;
  for (let j = lines.length - 1; j >= bodyStart; j--) {
    if (lines[j].startsWith('底本：') || lines[j].startsWith('底本:')) {
      bodyEnd = j;
      // 继续向上吞掉紧邻的空行
      while (bodyEnd > bodyStart && !lines[bodyEnd - 1].trim()) bodyEnd--;
      break;
    }
  }
  const colophon = lines.slice(bodyEnd).filter(l => l.trim());

  // --- 3. 逐行解析正文 ---
  const stats = { ruby: 0, note: 0, gaiji: 0, gaijiResolved: 0, lines: 0 };
  const body = [];
  for (let j = bodyStart; j < bodyEnd; j++) {
    const line = lines[j];
    if (!line.trim()) { body.push({ blank: true }); continue; }
    stats.lines++;
    body.push({ runs: parseLine(line, stats) });
  }

  return { meta, hasReimei, body, colophon, stats };
}

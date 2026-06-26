// 外字（字符集外字符）还原器
// 青空文库对 Shift_JIS 外字符使用注记，常见三种可还原形式：
//   U+XXXX                              直接 Unicode 码位（最可靠）
//   第3水準 / 第4水準 + 面-区-点          JIS X 0213 码位 → 查表
//   纯描述（无码位）                      无法还原，返回 null（保留占位符 + 描述）

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TABLE = path.resolve(__dirname, '..', 'data', 'jisx0213-2004-std.txt');

// 加载 JIS X 0213 面区点 → Unicode 映射
// 表 key 形如 "3-2121"(第1面) / "4-7032"(第2面)，值为 Unicode 十六进制
const table = new Map();
try {
  for (const line of fs.readFileSync(TABLE, 'utf8').split('\n')) {
    if (line.startsWith('#') || !line.trim()) continue;
    const [code, uni] = line.split('\t');
    if (uni && uni.startsWith('U+')) table.set(code, uni.slice(2));
  }
} catch {
  // 表缺失时降级：仅 U+ 形式可还原
}

function lookupMenKuTen(men, ku, ten) {
  const prefix = men === 1 ? '3' : '4';            // 第1面→3, 第2面→4
  const b1 = (ku + 0x20).toString(16).toUpperCase().padStart(2, '0');
  const b2 = (ten + 0x20).toString(16).toUpperCase().padStart(2, '0');
  return table.get(`${prefix}-${b1}${b2}`);        // Unicode hex 或 undefined
}

// 输入注记内容（不含 ※［＃ ］），返回还原后的字符串或 null
export function resolveGaiji(note) {
  // 1) U+XXXX 直接码位（兼容代理对范围内多码位极少见，取首个）
  const u = note.match(/U\+([0-9A-Fa-f]{4,6})/);
  if (u) {
    try { return String.fromCodePoint(parseInt(u[1], 16)); } catch { /* fallthrough */ }
  }
  // 2) 第N水準 + 面-区-点
  const m = note.match(/第[1-4]水準[^0-9]*?(\d)-(\d{1,2})-(\d{1,2})/);
  if (m) {
    const cp = lookupMenKuTen(+m[1], +m[2], +m[3]);
    if (cp) {
      try { return String.fromCodePoint(parseInt(cp, 16)); } catch { /* fallthrough */ }
    }
  }
  return null; // 无法还原
}

export const gaijiTableSize = table.size;

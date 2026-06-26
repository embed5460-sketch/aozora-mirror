# aozora — 日语公版名著阅读 App 数据流水线

面向 Google Play 日区的「公版日本名著纯净阅读 + 日语学习辅助」App 项目。
本仓库当前包含**数据预处理流水线**与项目策划文档；Android 客户端尚未开始。

完整发行策划见 [Document/需求/](Document/需求/)。

## 已验证的成果

对青空文库全量语料（19,489 件）做了合规过滤与解析压力测试：

| 指标 | 结果 |
|------|------|
| 双著作権フラグ公版（作品 + 人物均「なし」） | 18,561 件 |
| 本地文件存在的可发行书目 | **18,081 件** |
| 全库解析成功率 | 18,081 / 18,081（0 失败） |
| 提取 ruby（振假名） | 约 400 万个 |

合规要点：青空 repo 混有「CC 授权但著作权未消灭」的作品，因此**必须**按官方
元数据的「作品著作権フラグ」与「人物著作権フラグ」双重过滤，而非假设全部公版。

## 目录结构

```
Document/                 项目策划文档
pipeline/
  src/
    build-index.mjs       合规索引构建（CSV 过滤公版 + 映射本地文件）
    aozora-parser.mjs     青空格式解析（SHIFT_JIS→UTF-8、ruby/注释/外字）
    build-sample.mjs      抽样解析 + HTML 预览生成
  out/
    books-index.json      18,081 本结构化书目（已提交，核心成果）
  data/                   下载的元数据（git 忽略，见下方获取方式）
Tools/                    第三方原始语料（git 忽略，见下方获取方式）
```

## 环境

- Node.js 18+（用到原生 `TextDecoder('shift_jis')`，需完整 ICU，Node 默认即可）

## 从零复现

```bash
# 1. 获取青空文库纯文本语料（约 575MB）到 Tools/ 下
git clone --depth 1 https://github.com/aozorahack/aozorabunko_text.git \
  Tools/aozorabunko_text-master

# 2. 获取官方元数据 CSV 到 pipeline/data/
curl -L -o pipeline/data/list_person_all_extended_utf8.zip \
  https://www.aozora.gr.jp/index_pages/list_person_all_extended_utf8.zip
cd pipeline/data && unzip list_person_all_extended_utf8.zip && cd ../..

# 3. 构建合规索引
node pipeline/src/build-index.mjs

# 4. 抽样解析 + 生成预览（浏览器打开 pipeline/out/preview.html 验证 ruby 渲染）
node pipeline/src/build-sample.mjs 100

# 5. 构建 Cloudflare 可部署的数据层 cdn/（精选首发 4,798 本）
node pipeline/src/build-catalog.mjs            # 精选(curated)
# node pipeline/src/build-catalog.mjs --full   # 全量 18,081 本
```

## 数据层 cdn/

`build-catalog.mjs` 按[数据契约 v1](Document/DATA-CONTRACT.md)产出 Cloudflare Pages 站点B
可直接部署的目录树：`meta.json` + `catalog/{authors,books}.json` + `books/{id}.json`。

精选首发口径（[curation.mjs](pipeline/src/curation.mjs)）：**名家白名单 ∩ 新字新仮名**，
共 **4,798 本 / 42 位作者**，全为现代假名、对学习者友好。可改 `--full` 出全量。

外字：可还原的（JIS X 0213 / U+ 码位，约 63%）已在解析阶段还原为真字符；
约 37% 无码位外字以占位符 `〓` + 描述保留。

## 数据结构（books-index.json 单条）

```json
{
  "workId": "059898",
  "title": "ウェストミンスター寺院",
  "titleKana": "...",
  "author": "アーヴィングワシントン",
  "authorRoman": "Washington Irving",
  "birth": "1783-04-03",
  "death": "1859-11-28",
  "style": "新字新仮名",
  "file": "Tools/aozorabunko_text-master/cards/001257/files/59898_ruby_70679/59898_ruby_70679.txt"
}
```

## 版权声明

正文数据来自青空文库，均为著作权消灭作品。本项目与青空文库官方无关。
使用须遵循[「青空文庫収録ファイルの取り扱い規準」](https://www.aozora.gr.jp/guide/kijyunn.html)。

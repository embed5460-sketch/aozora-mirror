# 数据契约 v1（pipeline ↔ 客户端 ↔ Cloudflare）

本文件定义 pipeline 产出、客户端消费、Cloudflare 分发的数据格式。
所有文件 UTF-8 编码、JSON 格式；Cloudflare 自动 gzip/brotli，故不预压缩。

## CDN 目录树（Cloudflare Pages 站点B 根）

```
cdn/
  meta.json                 版本与统计信息
  catalog/
    authors.json            作者列表（浏览用）
    books.json              全量精简书目（浏览/检索用，不含正文）
  books/
    {id}.json               单本正文（结构化 blocks）
```

客户端启动时拉取 `meta.json` + `catalog/*.json`（随包内置一份兜底，联网后增量更新），
打开某本书时按需拉取 `books/{id}.json` 并本地缓存。

## meta.json

```json
{
  "version": 1,            // 数据契约版本
  "generatedAt": "",       // 由发布流程填充
  "edition": "curated",    // curated=精选首发 | full=全量
  "bookCount": 4801,
  "authorCount": 41,
  "schema": "v1"
}
```

## catalog/authors.json

```json
[
  {
    "id": "000035",        // 人物ID（青空 personId）
    "name": "太宰治",
    "kana": "だざいおさむ",
    "roman": "Osamu Dazai",
    "birth": "1909-06-19",
    "death": "1948-06-13",
    "count": 203           // 该作者在本 edition 中的作品数
  }
]
```

## catalog/books.json

精简书目，仅含浏览/检索所需字段（不含正文，控制体积）：

```json
[
  {
    "id": "002314",        // 作品ID（青空 workId），全局唯一，= books/{id}.json 文件名
    "t": "イズムの功過",   // 标题
    "tk": "いずむのこうか",// 标题读音（排序/检索用）
    "a": "000148",         // authorId（关联 authors.json）
    "c": "914"             // NDC 分类号（可空）
  }
]
```

## books/{id}.json —— 单本正文

```json
{
  "id": "002314",
  "title": "イズムの功過",
  "author": "夏目漱石",
  "authorId": "000148",
  "blocks": [ /* 见下 */ ]
}
```

### block（段落级）

| 字段 | 含义 |
|------|------|
| `{"k":"p","r":[run...]}`  | 段落（paragraph），`r` 为 run 数组 |
| `{"k":"h","r":[run...]}`  | 标题（heading，由「大/中/小見出し」注记识别） |
| `{"k":"blank"}`           | 空行 / 段间距 |

### run（行内片段）

| 字段 | 含义 |
|------|------|
| `{"t":"文字列"}`            | 普通文本 |
| `{"b":"基","f":"ふりがな"}` | 振假名（ruby）：`b`=基串，`f`=读音 |
| `{"g":"〓","d":"説明"}`     | 无法还原的外字：`g`=占位符，`d`=原始描述（可作 tooltip） |
| `{"n":"注記"}`              | 行内注记（如傍点说明等保留项） |

说明：
- 可还原的外字（JIS X 0213 / U+ 码位）已在解析阶段还原为真字符，并入 `{"t":...}`，
  客户端无需特殊处理。仅约 1/3 无码位的外字以 `{"g","d"}` 形式保留。
- 纯排版指令注记（字下げ、改丁、改ページ、左右中央等）在构建时已剔除，
  客户端按自身排版重新流式布局。

## 版本演进

- `meta.version` 变更表示不兼容的结构调整；客户端按版本协商。
- 新增可选字段不提升主版本。

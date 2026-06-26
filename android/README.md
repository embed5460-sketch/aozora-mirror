# android — 名作文庫 客户端骨架

Jetpack Compose 阅读器骨架，消费[数据契约 v1](../Document/DATA-CONTRACT.md)。
打通最小闭环：**作家列表 → 作品列表 → 阅读器（振假名渲染）+ 阅读设置**。

## 技术栈

| 项 | 版本 / 说明 |
|----|------|
| Gradle / AGP / Kotlin | 9.3.1 / 8.13.1 / 2.1.20 |
| compileSdk / minSdk / targetSdk | 36 / 24 / 36 |
| UI | Jetpack Compose（Material3 1.4.0、Foundation/UI 1.9.3、Navigation 2.9.4） |
| 序列化 / 网络 | kotlinx.serialization 1.6.3 / OkHttp 4.12.0 |
| 设置持久化 | SharedPreferences（暂未引 DataStore/Room） |

## 结构

```
app/src/main/java/app/meisaku/reader/
  MainActivity.kt              入口
  Graph.kt                     极简手动依赖容器（进程单例）
  data/
    model/Contract.kt          数据契约 v1 模型 + DTO→域模型映射
    Repository.kt              CatalogRepository / BookRepository（assets→缓存→CDN）
    SettingsStore.kt           阅读设置（SharedPreferences）
    Config.kt                  CDN base（部署站点B后填入）
  ui/
    MeisakuApp.kt              NavHost 路由
    theme/Theme.kt             和风配色（纸色/夜间）
    reader/RubyText.kt         ★ 原生 Compose 振假名渲染（TextMeasurer + 手动断行 + Canvas）
    screen/                    Library / AuthorBooks / Reader / Settings / SettingsPanel
app/src/main/assets/data/      内置兜底数据（catalog 全量 + 8 本样书，见下）
```

## 数据来源

- `assets/data/catalog/{authors,books}.json` + `meta.json`：全量精选目录（4,798 本 / 42 家），随包内置。
- `assets/data/books/{id}.json`：内置 **8 本样书**（漱石/太宰/芥川/賢治/中島/梶井/安吾/鴎外各一），离线即可读。
- 其余作品需部署 CDN 站点B 后，在 [Config.kt](app/src/main/java/app/meisaku/reader/data/Config.kt) 填入 `CDN_BASE`，
  `BookRepository` 会按需拉取并缓存到 `filesDir/books/`。

内置数据由仓库根的 `cdn/` 拷贝而来（`cdn/` 由 `pipeline/src/build-catalog.mjs` 生成）。
重新生成样书：参见根 README 第 5 步构建 `cdn/` 后，按需拷贝到 `assets/data/`。

## 构建

```bash
cd android
./gradlew :app:assembleDebug      # 产出 app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`（`sdk.dir`）为本机配置，已 git 忽略；用 Android Studio 打开 `android/` 会自动生成。

## 现状与后续

已实现：目录浏览、作家检索、按需加载正文、振假名横排渲染、字号/行距/夜间设置（持久化）。
未实现（后续）：纵書き、书签/历史（Room）、全文自動振假名（kuromoji）、AdMob、Play Billing、CDN 增量更新与版本协商。

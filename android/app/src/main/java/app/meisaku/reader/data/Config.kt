package app.meisaku.reader.data

/** 运行期配置。CDN 站点B 部署后填入 base，未部署时为空 → 仅用内置 assets。 */
object Config {
    /**
     * 线上 CDN（站点B）：Cloudflare Pages 静态托管，Build output directory=cdn，故根下直接是 /books/{id}.json。
     * 链路：本地 git push → Gitee → GitHub 镜像 → Cloudflare Pages 自动发布。末尾不带斜杠。
     * 缓存头见 cdn/_headers（books 正文长缓存）。
     * 本机调试可临时改 http://127.0.0.1:8080（python -m http.server + adb reverse tcp:8080）。
     */
    const val CDN_BASE: String = "https://aozora-cdn.pages.dev"

    /**
     * 法務ページ（站点A）：Cloudflare Pages 静态托管，Build output directory=legal。
     * 与 CDN 不同 Pages 项目（同一 GitHub 镜像仓库，仅输出目录不同）。末尾不带斜杠。
     * 注意 Pages 默认剥 .html 后缀：/index.html→/、/terms.html→/terms，故下面用 clean URL 避免 308。
     */
    const val LEGAL_BASE: String = "https://aozora-mirror.pages.dev"

    val PRIVACY_URL: String get() = "$LEGAL_BASE/"
    val TERMS_URL: String get() = "$LEGAL_BASE/terms"

    fun hasCdn(): Boolean = CDN_BASE.isNotBlank()

    fun hasLegal(): Boolean = LEGAL_BASE.isNotBlank()
}

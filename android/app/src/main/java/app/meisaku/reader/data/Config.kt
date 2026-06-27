package app.meisaku.reader.data

/** 运行期配置。CDN 站点B 部署后填入 base，未部署时为空 → 仅用内置 assets。 */
object Config {
    /**
     * 例：https://text.example.pages.dev （末尾不带斜杠）。
     * 开发期本机调试：python -m http.server + adb reverse tcp:8080 → http://127.0.0.1:8080
     * 部署 Cloudflare 后替换为线上 HTTPS 域名。
     */
    const val CDN_BASE: String = ""

    fun hasCdn(): Boolean = CDN_BASE.isNotBlank()
}

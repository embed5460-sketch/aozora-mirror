package app.meisaku.reader.data

/** 运行期配置。CDN 站点B 部署后填入 base，未部署时为空 → 仅用内置 assets。 */
object Config {
    /** 例：https://text.example.pages.dev （末尾不带斜杠）。 */
    const val CDN_BASE: String = ""

    fun hasCdn(): Boolean = CDN_BASE.isNotBlank()
}

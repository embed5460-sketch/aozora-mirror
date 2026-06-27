package app.meisaku.reader.data

/** 运行期配置。CDN 站点B 部署后填入 base，未部署时为空 → 仅用内置 assets。 */
object Config {
    /**
     * 线上 CDN（站点B）：Vercel 静态托管，Root Directory=cdn，故根下直接是 /books/{id}.json。
     * 链路：本地 git push → Gitee → GitHub 镜像 → Vercel 自动发布。末尾不带斜杠。
     * 本机调试可临时改 http://127.0.0.1:8080（python -m http.server + adb reverse tcp:8080）。
     */
    const val CDN_BASE: String = "https://aozora-mirror.vercel.app"

    fun hasCdn(): Boolean = CDN_BASE.isNotBlank()
}

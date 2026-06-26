package app.meisaku.reader

import android.content.Context
import app.meisaku.reader.data.BookRepository
import app.meisaku.reader.data.CatalogRepository
import app.meisaku.reader.data.SettingsStore

/** 极简手动依赖容器（进程级单例）。MainActivity 启动时 init。 */
object Graph {
    lateinit var catalog: CatalogRepository
        private set
    lateinit var books: BookRepository
        private set
    lateinit var settings: SettingsStore
        private set

    fun init(context: Context) {
        if (::catalog.isInitialized) return
        val app = context.applicationContext
        catalog = CatalogRepository(app)
        books = BookRepository(app)
        settings = SettingsStore(app)
    }
}

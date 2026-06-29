package app.meisaku.reader

import android.content.Context
import app.meisaku.reader.data.BillingManager
import app.meisaku.reader.data.BookRepository
import app.meisaku.reader.data.CatalogRepository
import app.meisaku.reader.data.FuriganaQuota
import app.meisaku.reader.data.ReadingStore
import app.meisaku.reader.data.SettingsStore

/** 极简手动依赖容器（进程级单例）。MainActivity 启动时 init。 */
object Graph {
    lateinit var catalog: CatalogRepository
        private set
    lateinit var books: BookRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var quota: FuriganaQuota
        private set
    lateinit var reading: ReadingStore
        private set
    lateinit var billing: BillingManager
        private set

    fun init(context: Context) {
        if (::catalog.isInitialized) return
        val app = context.applicationContext
        catalog = CatalogRepository(app)
        books = BookRepository(app)
        settings = SettingsStore(app)
        quota = FuriganaQuota(app)
        reading = ReadingStore(app)
        billing = BillingManager(
            app,
            onPremiumOwned = { quota.markPremiumOwned() },
            onPremiumRevoked = { quota.reclaimPurchasedPremium() },
        )
        billing.start()
    }
}

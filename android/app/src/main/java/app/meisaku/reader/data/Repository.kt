package app.meisaku.reader.data

import android.content.Context
import app.meisaku.reader.data.model.Author
import app.meisaku.reader.data.model.BookDoc
import app.meisaku.reader.data.model.BookDocDto
import app.meisaku.reader.data.model.BookSummary
import app.meisaku.reader.data.model.Meta
import app.meisaku.reader.data.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 目录仓库：内置 assets 优先（兜底数据），联网后可增量更新（后续接 CDN meta 版本协商）。
 * 资源布局：assets/data/{meta.json, catalog/authors.json, catalog/books.json, books/{id}.json}
 */
class CatalogRepository(private val context: Context) {

    @Volatile private var cache: Catalog? = null

    data class Catalog(
        val meta: Meta,
        val authors: List<Author>,
        val books: List<BookSummary>,
        val authorsById: Map<String, Author>,
        val booksByAuthor: Map<String, List<BookSummary>>,
    )

    suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        val meta = runCatching { json.decodeFromString<Meta>(readAsset("data/meta.json")) }
            .getOrDefault(Meta())
        val authors = json.decodeFromString<List<Author>>(readAsset("data/catalog/authors.json"))
        val books = json.decodeFromString<List<BookSummary>>(readAsset("data/catalog/books.json"))
        Catalog(
            meta = meta,
            authors = authors,
            books = books,
            authorsById = authors.associateBy { it.id },
            booksByAuthor = books.groupBy { it.authorId },
        ).also { cache = it }
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

/**
 * 单本正文仓库：assets 优先 → filesDir 缓存 → CDN 拉取。
 */
class BookRepository(private val context: Context) {

    private val httpClient by lazy { OkHttpClient() }
    private val cacheDir: File by lazy { File(context.filesDir, "books").apply { mkdirs() } }

    suspend fun load(id: String): BookDoc = withContext(Dispatchers.IO) {
        val raw = readBundled(id) ?: readCached(id) ?: fetchFromCdn(id)
        ?: throw IOException("book $id 不可用（未内置、无缓存、未配置 CDN）")
        json.decodeFromString<BookDocDto>(raw).toDomain()
    }

    private fun readBundled(id: String): String? = runCatching {
        context.assets.open("data/books/$id.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    private fun readCached(id: String): String? {
        val f = File(cacheDir, "$id.json")
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    private fun fetchFromCdn(id: String): String? {
        if (!Config.hasCdn()) return null
        val req = Request.Builder().url("${Config.CDN_BASE}/books/$id.json").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            File(cacheDir, "$id.json").writeText(body, Charsets.UTF_8) // 缓存
            return body
        }
    }
}

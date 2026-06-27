package app.meisaku.reader.data

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 一本书的阅读进度（兼作历史条目）。 */
@Serializable
data class ProgressEntry(
    val bookId: String,
    val title: String,
    val author: String,
    val atomIndex: Int,
    val fraction: Float,
    val updatedAt: Long,
)

/** 一条书签：定位用 atomIndex（跨重排稳定），snippet 为当页摘要。 */
@Serializable
data class BookmarkEntry(
    val id: Long,
    val bookId: String,
    val atomIndex: Int,
    val snippet: String,
    val createdAt: Long,
)

@Serializable
private data class ReadingData(
    val progress: Map<String, ProgressEntry> = emptyMap(),
    val bookmarks: List<BookmarkEntry> = emptyList(),
)

/**
 * 阅读进度 / 历史 / 书签的本地持久化。轻量 JSON 文件（filesDir/reading.json），
 * 内存维护 + Compose State 暴露（仿 [SettingsStore]），变更后异步整体写回（数据量极小）。
 * 进度与书签都存 atomIndex 而非页码：换字号/横竖切换会重新分页，但原子序号稳定。
 */
class ReadingStore(context: Context) {

    private val file = File(context.filesDir, "reading.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var data: ReadingData = load()

    /** 历史：按最近阅读时间降序。 */
    private val _progress = mutableStateOf(progressList())
    val progress: State<List<ProgressEntry>> = _progress

    /** 书签变更计数，供 UI 重组（书签按书查询，不便整表暴露）。 */
    private val _bookmarksRev = mutableStateOf(0)
    val bookmarksRev: State<Int> = _bookmarksRev

    private fun load(): ReadingData = runCatching {
        if (file.exists()) json.decodeFromString<ReadingData>(file.readText()) else ReadingData()
    }.getOrDefault(ReadingData())

    private fun persist() {
        val snapshot = data
        io.launch { runCatching { file.writeText(json.encodeToString(snapshot)) } }
    }

    private fun progressList(): List<ProgressEntry> =
        data.progress.values.sortedByDescending { it.updatedAt }

    fun getProgress(bookId: String): ProgressEntry? = data.progress[bookId]

    fun saveProgress(bookId: String, title: String, author: String, atomIndex: Int, fraction: Float) {
        val entry = ProgressEntry(bookId, title, author, atomIndex, fraction, System.currentTimeMillis())
        data = data.copy(progress = data.progress + (bookId to entry))
        _progress.value = progressList()
        persist()
    }

    fun removeProgress(bookId: String) {
        if (bookId !in data.progress) return
        data = data.copy(progress = data.progress - bookId)
        _progress.value = progressList()
        persist()
    }

    fun bookmarksOf(bookId: String): List<BookmarkEntry> =
        data.bookmarks.filter { it.bookId == bookId }.sortedBy { it.atomIndex }

    fun addBookmark(bookId: String, atomIndex: Int, snippet: String) {
        // 同一位置不重复添加。
        if (data.bookmarks.any { it.bookId == bookId && it.atomIndex == atomIndex }) return
        val bm = BookmarkEntry(System.currentTimeMillis(), bookId, atomIndex, snippet, System.currentTimeMillis())
        data = data.copy(bookmarks = data.bookmarks + bm)
        _bookmarksRev.value++
        persist()
    }

    fun deleteBookmark(id: Long) {
        data = data.copy(bookmarks = data.bookmarks.filterNot { it.id == id })
        _bookmarksRev.value++
        persist()
    }
}

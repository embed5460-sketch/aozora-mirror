package app.meisaku.reader.data

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.Calendar

/**
 * 全文自动振假名的免费配额：免费版每日 [FREE_DAILY] 部作品，付费版无限。
 * 「一次使用」= 当天对某部作品启用注音；同一部作品当天重复打开不重复计数。
 * 跨日自动重置。premium 标志现为占位（后续接 Play Billing）。
 */
class FuriganaQuota(context: Context) {

    private val prefs = context.getSharedPreferences("furigana_quota", Context.MODE_PRIVATE)

    /** 快照，供 UI 即时显示剩余次数。 */
    data class Snapshot(val premium: Boolean, val usedToday: Int, val remaining: Int)

    private val _state = mutableStateOf(snapshot())
    val state: State<Snapshot> = _state

    val premium: Boolean get() = prefs.getBoolean(KEY_PREMIUM, false)

    fun setPremium(on: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM, on).apply()
        _state.value = snapshot()
    }

    /** 当天能否为 [bookId] 启用注音（只查不消耗）。 */
    fun canUse(bookId: String): Boolean {
        if (premium) return true
        rolloverIfNeeded()
        val used = usedBooks()
        return bookId in used || used.size < FREE_DAILY
    }

    /** 尝试为 [bookId] 启用注音并计数；返回是否允许。已计入的书当天不重复消耗。 */
    fun tryUse(bookId: String): Boolean {
        if (premium) return true
        rolloverIfNeeded()
        val used = usedBooks().toMutableSet()
        if (bookId in used) return true
        if (used.size >= FREE_DAILY) return false
        used.add(bookId)
        prefs.edit().putStringSet(KEY_BOOKS, used).apply()
        _state.value = snapshot()
        return true
    }

    private fun usedBooks(): Set<String> = prefs.getStringSet(KEY_BOOKS, emptySet()) ?: emptySet()

    private fun today(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun rolloverIfNeeded() {
        if (prefs.getString(KEY_DATE, null) != today()) {
            prefs.edit().putString(KEY_DATE, today()).putStringSet(KEY_BOOKS, emptySet()).apply()
        }
    }

    private fun snapshot(): Snapshot {
        rolloverIfNeeded()
        val used = usedBooks().size
        val p = premium
        return Snapshot(
            premium = p,
            usedToday = used,
            remaining = if (p) Int.MAX_VALUE else (FREE_DAILY - used).coerceAtLeast(0),
        )
    }

    companion object {
        const val FREE_DAILY = 3
        private const val KEY_PREMIUM = "premium"
        private const val KEY_DATE = "date"
        private const val KEY_BOOKS = "booksToday"
    }
}

package app.meisaku.reader.data

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/** 阅读设置（字号 sp / 行距倍率 / 夜间）。 */
data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineSpacingMult: Float = 1.7f,
    val night: Boolean = false,
    /** 全文自动振假名（端上 kuromoji 生成）。免费版后续接每日次数限制，付费无限。 */
    val autoFurigana: Boolean = false,
) {
    companion object {
        const val MIN_FONT = 14f
        const val MAX_FONT = 30f
        const val MIN_SPACING = 1.3f
        const val MAX_SPACING = 2.4f
    }
}

/** SharedPreferences 持久化的设置仓库；以 Compose State 暴露，便于即时重组。 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    private val _state = mutableStateOf(
        ReaderSettings(
            fontSizeSp = prefs.getFloat(KEY_FONT, 18f),
            lineSpacingMult = prefs.getFloat(KEY_SPACING, 1.7f),
            night = prefs.getBoolean(KEY_NIGHT, false),
            autoFurigana = prefs.getBoolean(KEY_AUTO_FURIGANA, false),
        )
    )
    val state: State<ReaderSettings> = _state

    fun setFontSize(sp: Float) {
        val v = sp.coerceIn(ReaderSettings.MIN_FONT, ReaderSettings.MAX_FONT)
        _state.value = _state.value.copy(fontSizeSp = v)
        prefs.edit().putFloat(KEY_FONT, v).apply()
    }

    fun setLineSpacing(mult: Float) {
        val v = mult.coerceIn(ReaderSettings.MIN_SPACING, ReaderSettings.MAX_SPACING)
        _state.value = _state.value.copy(lineSpacingMult = v)
        prefs.edit().putFloat(KEY_SPACING, v).apply()
    }

    fun setNight(night: Boolean) {
        _state.value = _state.value.copy(night = night)
        prefs.edit().putBoolean(KEY_NIGHT, night).apply()
    }

    fun setAutoFurigana(on: Boolean) {
        _state.value = _state.value.copy(autoFurigana = on)
        prefs.edit().putBoolean(KEY_AUTO_FURIGANA, on).apply()
    }

    private companion object {
        const val KEY_FONT = "fontSizeSp"
        const val KEY_SPACING = "lineSpacingMult"
        const val KEY_NIGHT = "night"
        const val KEY_AUTO_FURIGANA = "autoFurigana"
    }
}

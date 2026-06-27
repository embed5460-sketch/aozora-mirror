package app.meisaku.reader.furigana

import app.meisaku.reader.data.model.Run
import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * 端上自动振假名引擎：用 kuromoji-ipadic 分词，取每个词的读音（片假名），
 * 对照原表层做「送り仮名対齐」，只在汉字段上加振假名，假名段保持原样。
 *
 * 源数据里已有的 [Run.Ruby] 为权威，原样保留；只对 [Run.Text] 做自动注音。
 * 词典加载较重（首次 tokenize 触发），故 Tokenizer 懒加载且仅构建一次；
 * 调用方应在后台线程（Dispatchers.Default）调用 [annotateRuns]。
 */
object FuriganaEngine {

    // Tokenizer.tokenize 线程安全；构建会加载 IPADIC 词典，仅一次。
    private val tokenizer: Tokenizer by lazy { Tokenizer() }

    /** 对一段 runs 自动注音：Text 拆分注音，其余类型原样保留。 */
    fun annotateRuns(runs: List<Run>): List<Run> =
        runs.flatMap { run ->
            when (run) {
                is Run.Text -> annotateText(run.text)
                else -> listOf(run)
            }
        }

    /** 预热词典（可在进入阅读器时后台调用，避免首次注音卡顿）。 */
    fun warmUp() {
        runCatching { tokenizer.tokenize("予熱") }
    }

    private fun annotateText(text: String): List<Run> {
        if (text.isBlank() || text.none { isKanji(it) }) return listOf(Run.Text(text))

        val out = ArrayList<Run>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out.add(Run.Text(plain.toString()))
                plain.setLength(0)
            }
        }

        for (token in tokenizer.tokenize(text)) {
            for (piece in piecesForToken(token.surface, token.reading)) {
                when (piece) {
                    is Piece.Plain -> plain.append(piece.text)
                    is Piece.Ruby -> {
                        flushPlain()
                        out.add(Run.Ruby(piece.base, piece.furigana))
                    }
                }
            }
        }
        flushPlain()
        return out
    }

    private sealed interface Piece {
        data class Plain(val text: String) : Piece
        data class Ruby(val base: String, val furigana: String) : Piece
    }

    /**
     * 把一个词（表层 [surface] + 片假名读音 [readingKata]）切成若干 Plain / Ruby 片段。
     * 思路：把表层按「汉字段 / 非汉字段」切块，用读音（转平假名）按非汉字段做锚点对齐，
     * 中间的汉字段分到对应读音。用正则：汉字段→(.+?)，假名段→其平假名字面量。
     */
    private fun piecesForToken(surface: String, readingKata: String?): List<Piece> {
        if (surface.none { isKanji(it) }) return listOf(Piece.Plain(surface))

        val reading = readingKata?.takeIf { it.isNotEmpty() && it != "*" }?.let { kataToHira(it) }
        // 无读音、或读音与表层一致（说明本就是假名为主）→ 不注音
        if (reading == null || reading == kataToHira(surface)) return listOf(Piece.Plain(surface))

        val segments = segment(surface)
        // 只有一整块且全是汉字：整体注音
        if (segments.size == 1 && segments[0].isKanji) {
            return listOf(Piece.Ruby(surface, reading))
        }

        val pattern = buildString {
            append('^')
            for (seg in segments) {
                if (seg.isKanji) append("(.+?)") else append(Regex.escape(kataToHira(seg.text)))
            }
            append('$')
        }
        val match = runCatching { Regex(pattern).find(reading) }.getOrNull()
            ?: return fallback(surface, reading, segments)

        val groups = match.groupValues.drop(1)
        val out = ArrayList<Piece>(segments.size)
        var gi = 0
        for (seg in segments) {
            if (seg.isKanji) {
                val furi = groups.getOrNull(gi).orEmpty()
                gi++
                if (furi.isEmpty()) out.add(Piece.Plain(seg.text))
                else out.add(Piece.Ruby(seg.text, furi))
            } else {
                out.add(Piece.Plain(seg.text))
            }
        }
        return out
    }

    /** 对齐失败时的兜底：只有单一汉字块就整体注音，否则放弃注音（避免错位的难看 ruby）。 */
    private fun fallback(surface: String, reading: String, segments: List<Seg>): List<Piece> {
        val kanjiSegs = segments.count { it.isKanji }
        return if (kanjiSegs == 1 && segments.size == 1) listOf(Piece.Ruby(surface, reading))
        else listOf(Piece.Plain(surface))
    }

    private data class Seg(val text: String, val isKanji: Boolean)

    /** 把字符串按「连续汉字 / 连续非汉字」切段。 */
    private fun segment(s: String): List<Seg> {
        val segs = ArrayList<Seg>()
        val buf = StringBuilder()
        var curKanji: Boolean? = null
        for (c in s) {
            val k = isKanji(c)
            if (curKanji == null || k == curKanji) {
                buf.append(c); curKanji = k
            } else {
                segs.add(Seg(buf.toString(), curKanji)); buf.setLength(0)
                buf.append(c); curKanji = k
            }
        }
        if (buf.isNotEmpty()) segs.add(Seg(buf.toString(), curKanji == true))
        return segs
    }

    private fun isKanji(c: Char): Boolean {
        val code = c.code
        return code in 0x4E00..0x9FFF ||   // CJK 统一汉字
            code in 0x3400..0x4DBF ||      // 扩展A
            c == '々' || c == '〆' || c == 'ヶ' || c == 'ヵ'
    }

    /** 片假名→平假名（用于读音与表层假名的统一匹配）。其余字符（长音ー、中点等）原样。 */
    private fun kataToHira(s: String): String = buildString(s.length) {
        for (c in s) {
            val code = c.code
            if (code in 0x30A1..0x30F6) append((code - 0x60).toChar()) else append(c)
        }
    }
}

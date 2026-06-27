package app.meisaku.reader.ui.reader

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import app.meisaku.reader.data.model.Block
import app.meisaku.reader.data.model.BlockKind
import app.meisaku.reader.data.model.Run

/**
 * 分页排版引擎。把整本书的 [Block] 连续排版、按视口尺寸切成离散页，横排与竖排共用一套
 * 「原子 → 断行/断列 → 切页」框架：
 *  - 横排：光标左→右、越界换行、行从上往下堆，行底越界即收页（沿用 RubyText 的思路）。
 *  - 竖排（縦書き）：列从右→左、列内字符上→下、ruby 画在基字右侧细列，列左缘越界即收页。
 * 纯函数、不依赖 Composable，可在 Dispatchers.Default 后台跑。drawText 用的颜色已烘焙进
 * 各 style，故测量结果可跨位置复用 → 用 [measure] 缓存大幅减少重复测量（长书才不卡）。
 */

/** 页内一个已定位的字形（绝对坐标，单位 px）。[ruby] 为振假名小字（生成书签摘要时跳过）。 */
class Glyph(val layout: TextLayoutResult, val x: Float, val y: Float, val ruby: Boolean = false)

/** 一页：若干字形 + 该页首个原子的全局序号（用于改设置重排后保持阅读位置）。 */
class Page(val glyphs: List<Glyph>, val firstAtomIndex: Int)

fun paginate(
    blocks: List<Block>,
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    headingStyle: TextStyle,
    lineSpacingMult: Float,
    vertical: Boolean,
    pageWidthPx: Float,
    pageHeightPx: Float,
): List<Page> {
    if (pageWidthPx <= 0f || pageHeightPx <= 0f) return emptyList()
    val atoms = decompose(blocks)
    if (atoms.isEmpty()) return listOf(Page(emptyList(), 0))
    val cache = MeasureCache(measurer)
    val rubyBase = baseStyle.copy(fontSize = baseStyle.fontSize * 0.5f)
    val rubyHead = headingStyle.copy(fontSize = headingStyle.fontSize * 0.5f)
    return if (vertical) {
        paginateVertical(atoms, cache, baseStyle, headingStyle, rubyBase, rubyHead, lineSpacingMult, pageWidthPx, pageHeightPx)
    } else {
        paginateHorizontal(atoms, cache, baseStyle, headingStyle, rubyBase, rubyHead, lineSpacingMult, pageWidthPx, pageHeightPx)
    }
}

/** 找出包含给定原子序号的页码（重排后跳回原阅读位置用）。 */
fun List<Page>.pageOfAtom(atomIndex: Int): Int {
    var p = 0
    for (i in indices) {
        if (this[i].firstAtomIndex <= atomIndex) p = i else break
    }
    return p
}

// --- 原子拆分 ---

/**
 * 排版的最小单元。Ruby 原子整体保留（base 多字一起排、furigana 跟随）；普通文本用 [tokenize]
 * 拆成 ASCII 整词 / CJK 逐码点，便于任意字符处换行换列。Blank 块产出一个占位原子（只占间隔）。
 */
private class Atom(
    val text: String,
    val ruby: String?,
    val heading: Boolean,
    val paragraphStart: Boolean,
    val blank: Boolean,
    val index: Int,
)

private fun decompose(blocks: List<Block>): List<Atom> {
    val out = ArrayList<Atom>()
    var idx = 0
    for (b in blocks) {
        if (b.kind == BlockKind.Blank) {
            out.add(Atom("", null, heading = false, paragraphStart = false, blank = true, index = idx++))
            continue
        }
        val heading = b.kind == BlockKind.Heading
        var first = true
        for (r in b.runs) {
            when (r) {
                is Run.Ruby -> {
                    out.add(Atom(r.base, r.furigana.ifBlank { null }, heading, first, false, idx++)); first = false
                }
                is Run.Text -> for (t in tokenize(r.text)) {
                    out.add(Atom(t, null, heading, first, false, idx++)); first = false
                }
                is Run.Gaiji -> {
                    out.add(Atom(r.placeholder, null, heading, first, false, idx++)); first = false
                }
                is Run.Note -> for (t in tokenize(r.note)) {
                    out.add(Atom(t, null, heading, first, false, idx++)); first = false
                }
            }
        }
    }
    return out
}

// --- 横排 ---

private fun paginateHorizontal(
    atoms: List<Atom>,
    cache: MeasureCache,
    baseStyle: TextStyle,
    headingStyle: TextStyle,
    rubyBase: TextStyle,
    rubyHead: TextStyle,
    spacing: Float,
    pageW: Float,
    pageH: Float,
): List<Page> {
    fun reserve(head: Boolean) = cache.measure("あ", if (head) rubyHead else rubyBase).size.height.toFloat()
    fun baseH(head: Boolean) = cache.measure("あ", if (head) headingStyle else baseStyle).size.height.toFloat()

    val pages = ArrayList<Page>()
    var glyphs = ArrayList<Glyph>()
    var pageFirstIndex = atoms.first().index
    var x = 0f
    var lineTop = 0f

    fun finishPage() {
        pages.add(Page(glyphs, pageFirstIndex)); glyphs = ArrayList()
    }
    fun pageBreak(nextIndex: Int) {
        finishPage(); lineTop = 0f; x = 0f; pageFirstIndex = nextIndex
    }

    for (a in atoms) {
        val head = a.heading
        val lineH = reserve(head) + baseH(head)
        val pitch = lineH * spacing

        if (a.blank) {
            if (x > 0f) { x = 0f; lineTop += pitch }
            lineTop += baseH(false) * spacing
            if (lineTop + reserve(false) + baseH(false) > pageH) pageBreak(a.index)
            continue
        }
        if (a.paragraphStart && x > 0f) { x = 0f; lineTop += pitch }
        if (a.text == " ") continue

        val baseStyleUse = if (head) headingStyle else baseStyle
        val rubyStyleUse = if (head) rubyHead else rubyBase
        val baseL = cache.measure(a.text, baseStyleUse)
        val rubyL = a.ruby?.let { cache.measure(it, rubyStyleUse) }
        val baseW = baseL.size.width.toFloat()
        val rubyW = rubyL?.size?.width?.toFloat() ?: 0f
        val unitW = maxOf(baseW, rubyW)

        if (x > 0f && x + unitW > pageW) { x = 0f; lineTop += pitch }
        if (glyphs.isNotEmpty() && lineTop + lineH > pageH) pageBreak(a.index)

        val rReserve = reserve(head)
        val baseX = x + (unitW - baseW) / 2f
        val rubyX = x + (unitW - rubyW) / 2f
        glyphs.add(Glyph(baseL, baseX, lineTop + rReserve))
        rubyL?.let { glyphs.add(Glyph(it, rubyX, lineTop, ruby = true)) }
        x += unitW
    }
    finishPage()
    return pages
}

// --- 竖排（縦書き） ---

private fun paginateVertical(
    atoms: List<Atom>,
    cache: MeasureCache,
    baseStyle: TextStyle,
    headingStyle: TextStyle,
    rubyBase: TextStyle,
    rubyHead: TextStyle,
    spacing: Float,
    pageW: Float,
    pageH: Float,
): List<Page> {
    // 列宽与列距按基础字号统一（标题列偶尔略宽，吃进列间空隙，v1 可接受）。
    val colBaseW = cache.measure("あ", baseStyle).size.width.toFloat()
    val colRubyW = cache.measure("あ", rubyBase).size.width.toFloat()
    val colWidth = colBaseW + colRubyW
    val colPitch = colWidth * spacing

    val pages = ArrayList<Page>()
    var glyphs = ArrayList<Glyph>()
    var pageFirstIndex = atoms.first().index
    var colRightX = pageW
    var y = 0f

    fun finishPage() {
        pages.add(Page(glyphs, pageFirstIndex)); glyphs = ArrayList()
    }
    // 移到下一列（向左）；越过左缘则收页、回到最右列。
    fun newColumn(curIndex: Int) {
        colRightX -= colPitch
        y = 0f
        if (colRightX - colWidth < 0f) {
            finishPage(); colRightX = pageW; pageFirstIndex = curIndex
        }
    }

    var first = true
    for (a in atoms) {
        if (a.blank) {
            if (!first) newColumn(a.index)
            continue
        }
        if (a.paragraphStart && !first) newColumn(a.index)
        first = false

        val head = a.heading
        val baseStyleUse = if (head) headingStyle else baseStyle
        val rubyStyleUse = if (head) rubyHead else rubyBase
        val baseLayouts = splitCodepoints(a.text).map { cache.measure(it, baseStyleUse) }
        if (baseLayouts.isEmpty()) continue
        val totalH = baseLayouts.sumOf { it.size.height.toDouble() }.toFloat()

        // 整个原子放不下当前列剩余空间 → 换列（单原子超过整页则强排，避免死循环）。
        if (y > 0f && y + totalH > pageH) newColumn(a.index)

        var cy = y
        for (bl in baseLayouts) {
            val bw = bl.size.width.toFloat()
            val baseX = colRightX - colWidth + (colBaseW - bw) / 2f
            glyphs.add(Glyph(bl, baseX, cy))
            cy += bl.size.height.toFloat()
        }
        // ruby 在基字右侧细列，沿 base 跨度居中竖排。
        a.ruby?.let { rb ->
            val rubyLayouts = splitCodepoints(rb).map { cache.measure(it, rubyStyleUse) }
            val rTotal = rubyLayouts.sumOf { it.size.height.toDouble() }.toFloat()
            var ry = y + ((cy - y) - rTotal) / 2f
            for (rl in rubyLayouts) {
                val rw = rl.size.width.toFloat()
                glyphs.add(Glyph(rl, colRightX - rw, ry, ruby = true))
                ry += rl.size.height.toFloat()
            }
        }
        y = cy
    }
    finishPage()
    return pages
}

// --- 工具 ---

/** 测量缓存：同一 (文本, 样式) 只测一次。drawText 在任意 topLeft 重定位，故结果可跨位置复用。 */
private class MeasureCache(private val measurer: TextMeasurer) {
    private val cache = HashMap<Key, TextLayoutResult>()
    private data class Key(val text: String, val style: TextStyle)
    fun measure(text: String, style: TextStyle): TextLayoutResult =
        cache.getOrPut(Key(text, style)) { measurer.measure(text, style) }
}

/** 逐码点切分（CJK 每字一单元；竖排逐字堆叠用）。 */
private fun splitCodepoints(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val n = Character.charCount(cp)
        out.add(text.substring(i, i + n))
        i += n
    }
    return out
}

/**
 * 简易分词：ASCII 单词整体成单元（避免拉丁词被逐字拆开），其余（含 CJK）逐码点成单元，
 * 以便日文在任意字符处换行/换列。与旧 RubyText 行为一致。
 */
private fun tokenize(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val count = Character.charCount(cp)
        val s = text.substring(i, i + count)
        val isAsciiWord = cp < 128 && Character.isLetterOrDigit(cp)
        if (isAsciiWord) {
            sb.append(s)
        } else {
            if (sb.isNotEmpty()) { out.add(sb.toString()); sb.setLength(0) }
            out.add(s)
        }
        i += count
    }
    if (sb.isNotEmpty()) out.add(sb.toString())
    return out
}

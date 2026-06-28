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

/**
 * 页内一个已定位的字形（绝对坐标，单位 px）。[ruby] 为振假名小字（生成书签摘要时跳过）。
 * [rotate] 为真时绘制端绕 (x,y) 顺时针旋 90°（竖排里的长音符/括弧/破折号/侧旋拉丁词）。
 */
class Glyph(
    val layout: TextLayoutResult,
    val x: Float,
    val y: Float,
    val ruby: Boolean = false,
    val rotate: Boolean = false,
)

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
    // 一格全角字高（纵中横/旋转/句読点的步进与居中基准）。
    val colCharH = cache.measure("あ", baseStyle).size.height.toFloat()
    val headCharH = cache.measure("あ", headingStyle).size.height.toFloat()

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
        if (a.text.isBlank()) { first = false; continue }
        first = false

        val head = a.heading
        val baseStyleUse = if (head) headingStyle else baseStyle
        val rubyStyleUse = if (head) rubyHead else rubyBase
        val cellH = if (head) headCharH else colCharH

        // 1) 先排出该原子各子字形的「相对配方」(dx 相对 colLeft、dy 相对列游标、是否旋转)，
        //    并算出总竖向步进 adv —— 全部不依赖当前 colRightX，便于先做越界判断。
        val recipe = ArrayList<VGlyph>()
        var c = 0f // 列内累计偏移
        // 带 ruby 的原子一律走 Normal 逐字（ruby 只挂 CJK base）。
        val kind = if (a.ruby != null) AtomKind.Normal else classifyAtom(a.text)
        when (kind) {
            AtomKind.TateChuYoko -> {
                var tcy = cache.measure(a.text, baseStyleUse)
                var tw = tcy.size.width.toFloat()
                var th = tcy.size.height.toFloat()
                if (tw > colBaseW) { // 3 位等过宽 → 缩放塞进一格，避免溢入邻列
                    val st = baseStyleUse.copy(fontSize = baseStyleUse.fontSize * (colBaseW / tw))
                    tcy = cache.measure(a.text, st); tw = tcy.size.width.toFloat(); th = tcy.size.height.toFloat()
                }
                recipe.add(VGlyph(tcy, (colBaseW - tw) / 2f, c + (cellH - th) / 2f, false))
                c += cellH
            }
            AtomKind.SidewaysWord -> {
                val wl = cache.measure(a.text, baseStyleUse)
                val ww = wl.size.width.toFloat()
                val wh = wl.size.height.toFloat()
                // 旋 90° CW，pivot=topLeft=字条右上角：dx = 左缘 + h。
                recipe.add(VGlyph(wl, (colBaseW - wh) / 2f + wh, c, true))
                c += ww
            }
            AtomKind.Normal -> for (s in splitCodepoints(a.text)) {
                val bl = cache.measure(s, baseStyleUse)
                val bw = bl.size.width.toFloat()
                val bh = bl.size.height.toFloat()
                when (classifyCp(s.codePointAt(0))) {
                    CpKind.RotateCW -> {
                        recipe.add(VGlyph(bl, (colBaseW - bh) / 2f + bh, c, true))
                        c += bw // 旋转后竖向占据 = 原横向宽度
                    }
                    CpKind.ReposTopRight -> {
                        recipe.add(VGlyph(bl, (colBaseW - bw) + K_PUNCT_RIGHT * colBaseW, c - K_PUNCT_UP * cellH, false))
                        c += cellH
                    }
                    CpKind.PlainStack -> {
                        recipe.add(VGlyph(bl, (colBaseW - bw) / 2f, c, false))
                        c += bh
                    }
                }
            }
        }
        if (recipe.isEmpty()) continue
        val adv = c

        // 2) 该原子放不下当前列剩余空间 → 换列（单原子超过整页则强排，避免死循环）。
        if (y > 0f && y + adv > pageH) newColumn(a.index)

        // 3) 收页后再用最终 colLeft 落绝对坐标。
        val colLeft = colRightX - colWidth
        for (g in recipe) {
            glyphs.add(Glyph(g.layout, colLeft + g.dx, y + g.dy, rotate = g.rotate))
        }
        // ruby 在基字右侧细列，沿原子跨度 [y, y+adv] 居中竖排。
        a.ruby?.let { rb ->
            val rubyLayouts = splitCodepoints(rb).map { cache.measure(it, rubyStyleUse) }
            val rTotal = rubyLayouts.sumOf { it.size.height.toDouble() }.toFloat()
            var ry = y + (adv - rTotal) / 2f
            for (rl in rubyLayouts) {
                val rw = rl.size.width.toFloat()
                glyphs.add(Glyph(rl, colRightX - rw, ry, ruby = true))
                ry += rl.size.height.toFloat()
            }
        }
        y += adv
    }
    finishPage()
    return pages
}

/** 竖排原子内一个子字形的相对配方：dx 相对列左缘、dy 相对列游标、[rotate] 是否绕左上顺时针 90°。 */
private class VGlyph(val layout: TextLayoutResult, val dx: Float, val dy: Float, val rotate: Boolean)

/** 句読点右上偏移常数（真机微调）。ink 在 em 框左下，右移+上移让其落到字格右上。 */
private const val K_PUNCT_RIGHT = 0.30f
private const val K_PUNCT_UP = 0.55f

private enum class AtomKind { TateChuYoko, SidewaysWord, Normal }
private enum class CpKind { RotateCW, ReposTopRight, PlainStack }

/** 整词分类：短半角数字→纵中横；长数字/拉丁词→侧旋；其余→逐字。 */
private fun classifyAtom(text: String): AtomKind {
    if (text.isEmpty()) return AtomKind.Normal
    val cps = splitCodepoints(text).size
    val allDigits = text.all { it in '0'..'9' }
    // ASCII 可打印串（含空格/标点），且至少含一个字母或数字 → 整词侧旋（词间空格随之保留）。
    val asciiRun = text.all { it.code in 0x20..0x7E } && text.any { it.isLetterOrDigit() }
    return when {
        allDigits && cps in 1..3 -> AtomKind.TateChuYoko
        asciiRun && cps >= 2 -> AtomKind.SidewaysWord
        else -> AtomKind.Normal
    }
}

private val ROTATE_CW: Set<Int> = setOf(
    0x30FC,                         // ー 長音符
    0x301C, 0xFF5E,                 // 〜 ～
    0x2014, 0x2015, 0x2013, 0x2010, // — ― – ‐
    0x2026, 0x2025,                 // … ‥
    0x300C, 0x300D, 0x300E, 0x300F, // 「」『』
    0xFF08, 0xFF09, 0x0028, 0x0029, // （）()
    0x3014, 0x3015, 0xFF3B, 0xFF3D, // 〔〕［］
    0xFF5B, 0xFF5D, 0x3008, 0x3009, // ｛｝〈〉
    0x300A, 0x300B, 0x3010, 0x3011, // 《》【】
)
private val REPOS_TOPRIGHT: Set<Int> = setOf(0x3002, 0x3001, 0xFF0C, 0xFF0E) // 。、，．

private fun classifyCp(cp: Int): CpKind = when (cp) {
    in REPOS_TOPRIGHT -> CpKind.ReposTopRight
    in ROTATE_CW -> CpKind.RotateCW
    else -> CpKind.PlainStack // ！？・小假名・漢字假名
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
 * 简易分词：连续 ASCII 可打印字符（字母/数字/空格/标点）整体成一个单元 —— 让拉丁短语连同
 * 词间空格留在同一原子里（竖排侧旋、横排都需正确词距，单字母也不再被拆出直立）；其余（含 CJK）
 * 逐码点成单元，以便日文在任意字符处换行/换列。纯日文文本因全是非 ASCII，分词结果不变。
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
        val isAsciiPrintable = cp in 0x20..0x7E
        if (isAsciiPrintable) {
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

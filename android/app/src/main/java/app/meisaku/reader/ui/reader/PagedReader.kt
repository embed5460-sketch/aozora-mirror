package app.meisaku.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meisaku.reader.data.model.Block
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/**
 * 按页阅读器：把 [blocks] 经 [paginate] 排成离散页，用 [HorizontalPager] 翻页。
 *  - 横排正向、竖排 reverseLayout（右起翻页方向正确）。
 *  - 翻页动画 = Pager 原生滑动惯性 + 每页 graphicsLayer 缩放/变暗（层叠立体感）。
 *  - 点屏幕中央切换 chrome（顶/底栏）显隐；两侧点击翻页；底栏 Slider 跳页。
 *  - 改字号/行距/横竖切换会重排，重排前后用原子序号锚点保持阅读位置。
 */
@Composable
fun PagedReader(
    blocks: List<Block>,
    fontSizeSp: Float,
    lineSpacingMult: Float,
    vertical: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
    initialAtomIndex: Int = 0,
    onProgress: (atomIndex: Int, fraction: Float, snippet: String) -> Unit = { _, _, _ -> },
    jumpToAtom: Int? = null,
    onJumpHandled: () -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val baseColor = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    val baseStyle = remember(fontSizeSp, baseColor) {
        TextStyle(fontSize = fontSizeSp.sp, color = baseColor)
    }
    val headingStyle = remember(fontSizeSp, accent) {
        TextStyle(fontSize = (fontSizeSp * 1.2f).sp, color = accent, fontWeight = FontWeight.Bold)
    }

    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // 首次分页用外部传入的续读位置定位；之后重排用 live 锚点保持当前位置。
    var hasAppliedInitial by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    BoxWithConstraints(modifier.fillMaxSize().background(bg)) {
        val padX = with(density) { 22.dp.toPx() }
        val padY = with(density) { 16.dp.toPx() }
        val contentW = constraints.maxWidth - padX * 2f
        val contentH = constraints.maxHeight - padY * 2f

        LaunchedEffect(blocks, fontSizeSp, lineSpacingMult, vertical, contentW, contentH) {
            // 续读：首次用 initialAtomIndex；重排：抓当前页首原子做锚点跳回同一位置。
            val anchor = if (!hasAppliedInitial) initialAtomIndex
            else pages.getOrNull(pagerState.currentPage)?.firstAtomIndex ?: 0
            loading = true
            val newPages = withContext(Dispatchers.Default) {
                paginate(
                    blocks, measurer, baseStyle, headingStyle, lineSpacingMult,
                    vertical, contentW, contentH,
                )
            }
            pages = newPages
            loading = false
            if (newPages.isNotEmpty()) {
                pagerState.scrollToPage(newPages.pageOfAtom(anchor).coerceIn(0, newPages.size - 1))
                hasAppliedInitial = true
            }
        }

        // 翻页/续读后回传当前位置（atomIndex + 进度% + 当页摘要），供保存进度与加书签。
        LaunchedEffect(pagerState.currentPage, pages) {
            if (pages.isEmpty()) return@LaunchedEffect
            val page = pages.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
            val fraction = if (pages.size <= 1) 1f else pagerState.currentPage.toFloat() / (pages.size - 1)
            onProgress(page.firstAtomIndex, fraction, snippetOf(page))
        }

        // 书签跳转（一次性）。
        LaunchedEffect(jumpToAtom, pages) {
            val target = jumpToAtom ?: return@LaunchedEffect
            if (pages.isEmpty()) return@LaunchedEffect
            pagerState.scrollToPage(pages.pageOfAtom(target).coerceIn(0, pages.size - 1))
            onJumpHandled()
        }

        when {
            loading && pages.isEmpty() -> LinearProgressIndicator(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
            )
            pages.isEmpty() -> Unit
            else -> {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = vertical,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    val page = pages[index]
                    Box(
                        Modifier
                            .fillMaxSize()
                            // 点击切菜单：只消费 tap，拖动继续上传给 Pager 翻页（不抢滑动）。
                            .pointerInput(Unit) { detectTapGestures { onToggleChrome() } }
                            .graphicsLayer {
                                // 距当前页越远越缩小、越淡、并轻微视差错位 → 明显的层叠纵深感。
                                val signed = (index - pagerState.currentPage) -
                                    pagerState.currentPageOffsetFraction
                                val off = signed.absoluteValue.coerceIn(0f, 1f)
                                val s = 1f - 0.22f * off
                                scaleX = s; scaleY = s
                                alpha = 1f - 0.5f * off
                                // 让离开的页比 Pager 自身平移慢一拍，制造叠放错位。
                                translationX = -signed * size.width * 0.18f
                            }
                            .background(bg),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            for (g in page.glyphs) {
                                val tl = Offset(g.x + padX, g.y + padY)
                                if (g.rotate) {
                                    rotate(degrees = 90f, pivot = tl) {
                                        drawText(g.layout, topLeft = tl)
                                    }
                                } else {
                                    drawText(g.layout, topLeft = tl)
                                }
                            }
                        }
                    }
                }

                PageScrubber(
                    visible = chromeVisible,
                    current = pagerState.currentPage,
                    total = pages.size,
                    onJump = { scope.launch { pagerState.scrollToPage(it) } },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // 全屏（收起菜单）时仍显示一个淡淡的常驻页码，不打扰阅读。
                if (!chromeVisible) {
                    Text(
                        "${pagerState.currentPage + 1} / ${pages.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                    )
                }
            }
        }
    }
}

/** 取当页开头的正文字符作书签摘要（跳过 ruby 小字），截断到 ~24 字。 */
private fun snippetOf(page: Page): String {
    val sb = StringBuilder()
    for (g in page.glyphs) {
        if (g.ruby) continue
        sb.append(g.layout.layoutInput.text.text)
        if (sb.length >= 24) break
    }
    return sb.toString().trim().take(24)
}

/** 底部跳页条：页码 + 可拖动 Slider，随 chrome 显隐上下滑入滑出。 */
@Composable
private fun PageScrubber(
    visible: Boolean,
    current: Int,
    total: Int,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        var dragging by remember { mutableStateOf<Float?>(null) }
        val shown = (dragging?.toInt() ?: current).coerceIn(0, (total - 1).coerceAtLeast(0))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (total > 1) {
                    Slider(
                        value = (dragging ?: current.toFloat()).coerceIn(0f, (total - 1).toFloat()),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            dragging?.let { onJump(it.toInt().coerceIn(0, total - 1)) }
                            dragging = null
                        },
                        valueRange = 0f..(total - 1).toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "${shown + 1} / $total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

package app.meisaku.reader.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.ContextWrapper
import app.meisaku.reader.Graph
import app.meisaku.reader.data.FuriganaQuota
import app.meisaku.reader.data.model.Block
import app.meisaku.reader.data.model.BookDoc
import app.meisaku.reader.furigana.FuriganaEngine
import app.meisaku.reader.ui.reader.PagedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
) {
    val settings by Graph.settings.state
    var doc by remember(bookId) { mutableStateOf<BookDoc?>(null) }
    var error by remember(bookId) { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    // 顶/底栏（chrome）显隐：点屏幕切换，沉浸阅读。
    var chromeVisible by remember { mutableStateOf(true) }
    // 实际渲染的 blocks：自动注音关→原文；开→后台 kuromoji 生成后替换。
    var displayBlocks by remember(bookId) { mutableStateOf<List<Block>?>(null) }
    var annotating by remember(bookId) { mutableStateOf(false) }
    // 自动注音开着、但今日免费次数用尽且本书未用过 → 显示原文并提示。
    var quotaBlocked by remember(bookId) { mutableStateOf(false) }
    // 看激励广告解锁本书后 +1，触发重新注音。
    var reload by remember(bookId) { mutableStateOf(0) }

    // 续读位置（打开时取一次）、当前位置/摘要（供保存进度与加书签）、书签跳转。
    val initialAtom = remember(bookId) { Graph.reading.getProgress(bookId)?.atomIndex ?: 0 }
    var curAtom by remember(bookId) { mutableStateOf(initialAtom) }
    var curSnippet by remember(bookId) { mutableStateOf("") }
    var pendingJump by remember(bookId) { mutableStateOf<Int?>(null) }

    androidx.compose.runtime.LaunchedEffect(bookId) {
        doc = null; error = null
        try {
            doc = Graph.books.load(bookId)
        } catch (e: Exception) {
            error = e.message ?: "読み込みに失敗しました"
        }
    }

    androidx.compose.runtime.LaunchedEffect(doc, settings.autoFurigana, reload) {
        val d = doc
        if (d == null) {
            displayBlocks = null
        } else if (!settings.autoFurigana) {
            displayBlocks = d.blocks
            annotating = false
            quotaBlocked = false
        } else if (!Graph.quota.tryUse(bookId)) {
            displayBlocks = d.blocks
            annotating = false
            quotaBlocked = true
        } else {
            quotaBlocked = false
            annotating = true
            val annotated = withContext(Dispatchers.Default) {
                d.blocks.map { b ->
                    if (b.runs.isEmpty()) b else b.copy(runs = FuriganaEngine.annotateRuns(b.runs))
                }
            }
            displayBlocks = annotated
            annotating = false
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
            ) {
                androidx.compose.material3.TopAppBar(
                    title = { Text(doc?.title ?: "", maxLines = 1, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
                    actions = {
                        TextButton(onClick = { showBookmarks = true }) { Text("しおり") }
                        TextButton(onClick = { showSettings = true }) { Text("Aa") }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                error != null -> Text(
                    error!!,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                doc == null -> LoadingBox(Modifier.fillMaxSize())
                else -> {
                    val d = doc!!
                    val adState by Graph.ads.state
                    val context = LocalContext.current
                    Column(Modifier.fillMaxSize()) {
                        if (quotaBlocked) {
                            QuotaBanner(
                                rewardedReady = adState.rewardedReady,
                                onWatchAd = {
                                    context.findActivity()?.let { act ->
                                        Graph.ads.showRewarded(act) {
                                            Graph.quota.unlockBook(bookId)
                                            reload++
                                        }
                                    }
                                },
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            PagedReader(
                                blocks = displayBlocks ?: d.blocks,
                                fontSizeSp = settings.fontSizeSp,
                                lineSpacingMult = settings.lineSpacingMult,
                                vertical = settings.verticalWriting,
                                chromeVisible = chromeVisible,
                                onToggleChrome = { chromeVisible = !chromeVisible },
                                initialAtomIndex = initialAtom,
                                onProgress = { atom, frac, snip ->
                                    curAtom = atom
                                    curSnippet = snip
                                    Graph.reading.saveProgress(bookId, d.title, d.author, atom, frac)
                                },
                                jumpToAtom = pendingJump,
                                onJumpHandled = { pendingJump = null },
                            )
                        }
                    }
                }
            }
            // 首次自动注音时词典加载/分词需片刻，顶部给个细进度条。
            if (annotating) {
                androidx.compose.material3.LinearProgressIndicator(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter),
                )
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            ReaderSettingsPanel()
        }
    }

    if (showBookmarks) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarks = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            BookmarkSheet(
                bookId = bookId,
                currentAtom = curAtom,
                currentSnippet = curSnippet,
                onJump = { atom -> pendingJump = atom; showBookmarks = false },
            )
        }
    }
}

@Composable
private fun BookmarkSheet(
    bookId: String,
    currentAtom: Int,
    currentSnippet: String,
    onJump: (Int) -> Unit,
) {
    val rev by Graph.reading.bookmarksRev
    val list = remember(rev, bookId) { Graph.reading.bookmarksOf(bookId) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("しおり", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { Graph.reading.addBookmark(bookId, currentAtom, currentSnippet) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("現在のページをしおりに追加")
        }
        Spacer(Modifier.height(12.dp))

        if (list.isEmpty()) {
            Text(
                "まだしおりがありません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                list.forEachIndexed { i, bm ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            bm.snippet.ifBlank { "位置 ${bm.atomIndex}" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onJump(bm.atomIndex) }
                                .padding(vertical = 14.dp),
                        )
                        TextButton(onClick = { Graph.reading.deleteBookmark(bm.id) }) { Text("削除") }
                    }
                }
            }
        }
    }
}

/** 从 Compose 的 Context 解包宿主 Activity（展示全屏广告需要）。 */
private fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * 今日免费注音用尽时的横幅。[rewardedReady] 为真时给出「広告を見て表示」按钮，看完解锁本作品；
 * 否则只显示文字提示（引导去买 premium）。
 */
@Composable
private fun QuotaBanner(rewardedReady: Boolean, onWatchAd: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "本日の無料ふりがなは ${FuriganaQuota.FREE_DAILY} 作品まで使い切りました。" +
                    "広告を見るとこの作品のふりがなを表示できます（設定からプレミアムにすると無制限）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (rewardedReady) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onWatchAd, modifier = Modifier.fillMaxWidth()) {
                    Text("広告を見てこの作品のふりがなを表示")
                }
            }
        }
    }
}

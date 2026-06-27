package app.meisaku.reader.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    // 顶/底栏（chrome）显隐：点屏幕中央切换，沉浸阅读。
    var chromeVisible by remember { mutableStateOf(true) }
    // 实际渲染的 blocks：自动注音关→原文；开→后台 kuromoji 生成后替换。
    var displayBlocks by remember(bookId) { mutableStateOf<List<Block>?>(null) }
    var annotating by remember(bookId) { mutableStateOf(false) }
    // 自动注音开着、但今日免费次数用尽且本书未用过 → 显示原文并提示。
    var quotaBlocked by remember(bookId) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(bookId) {
        doc = null; error = null
        try {
            doc = Graph.books.load(bookId)
        } catch (e: Exception) {
            error = e.message ?: "読み込みに失敗しました"
        }
    }

    androidx.compose.runtime.LaunchedEffect(doc, settings.autoFurigana) {
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
                TopAppBar(
                    title = { Text(doc?.title ?: "", maxLines = 1, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
                    actions = { TextButton(onClick = { showSettings = true }) { Text("Aa") } },
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
                else -> Column(Modifier.fillMaxSize()) {
                    if (quotaBlocked) QuotaBanner()
                    Box(Modifier.weight(1f)) {
                        PagedReader(
                            blocks = displayBlocks ?: doc!!.blocks,
                            fontSizeSp = settings.fontSizeSp,
                            lineSpacingMult = settings.lineSpacingMult,
                            vertical = settings.verticalWriting,
                            chromeVisible = chromeVisible,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                        )
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
}

@Composable
private fun QuotaBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            "本日の無料ふりがなは ${FuriganaQuota.FREE_DAILY} 作品まで使い切りました。" +
                "設定からプレミアムにすると無制限にご利用いただけます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

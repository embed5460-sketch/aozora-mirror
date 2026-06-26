package app.meisaku.reader.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meisaku.reader.Graph
import app.meisaku.reader.data.model.Block
import app.meisaku.reader.data.model.BlockKind
import app.meisaku.reader.data.model.BookDoc
import app.meisaku.reader.ui.reader.RubyParagraph

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

    androidx.compose.runtime.LaunchedEffect(bookId) {
        doc = null; error = null
        try {
            doc = Graph.books.load(bookId)
        } catch (e: Exception) {
            error = e.message ?: "読み込みに失敗しました"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc?.title ?: "", maxLines = 1, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
                actions = { TextButton(onClick = { showSettings = true }) { Text("Aa") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
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
                else -> BookBody(doc!!, settings.fontSizeSp, settings.lineSpacingMult)
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
private fun BookBody(doc: BookDoc, fontSizeSp: Float, lineSpacingMult: Float) {
    val baseColor = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    val headerSurface = MaterialTheme.colorScheme.surfaceContainer
    val baseStyle = remember(fontSizeSp, baseColor) {
        TextStyle(fontSize = fontSizeSp.sp, color = baseColor)
    }
    // 章節見出しは強調色で本文と差別化
    val headingStyle = remember(fontSizeSp, accent) {
        TextStyle(fontSize = (fontSizeSp * 1.2f).sp, color = accent, fontWeight = FontWeight.Bold)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            // 書名ヘッダー：微表面カードで立体感、書名は強調色
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                color = headerSurface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                    Text(
                        doc.title,
                        style = TextStyle(
                            fontSize = (fontSizeSp * 1.5f).sp,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(doc.author, style = baseStyle.copy(fontSize = (fontSizeSp * 0.9f).sp, color = subColor))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        itemsIndexed(doc.blocks) { _, block ->
            BlockView(block, baseStyle, headingStyle, lineSpacingMult)
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun BlockView(
    block: Block,
    baseStyle: TextStyle,
    headingStyle: TextStyle,
    lineSpacingMult: Float,
) {
    when (block.kind) {
        BlockKind.Blank -> Spacer(Modifier.height(12.dp))
        BlockKind.Heading -> {
            Spacer(Modifier.height(16.dp))
            RubyParagraph(
                runs = block.runs,
                style = headingStyle,
                lineSpacingMult = lineSpacingMult,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        BlockKind.Paragraph -> RubyParagraph(
            runs = block.runs,
            style = baseStyle,
            lineSpacingMult = lineSpacingMult,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
    }
}

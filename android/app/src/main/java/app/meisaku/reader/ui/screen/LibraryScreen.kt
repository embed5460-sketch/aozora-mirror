package app.meisaku.reader.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.meisaku.reader.Graph
import app.meisaku.reader.data.CatalogRepository
import app.meisaku.reader.data.ProgressEntry
import app.meisaku.reader.data.model.Author
import app.meisaku.reader.ui.component.AuthorAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAuthor: (String) -> Unit,
    onBook: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val catalog = rememberCatalog()
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val recent by Graph.reading.progress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("名作文庫", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = onSettings) { Text("設定") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val c = catalog) {
            null -> LoadingBox(Modifier.fillMaxSize().padding(padding))
            else -> {
                val q = query.text.trim()
                val authors = remember(c, q) {
                    if (q.isEmpty()) c.authors
                    else c.authors.filter {
                        it.name.contains(q) ||
                            (it.kana?.contains(q) == true) ||
                            (it.roman?.contains(q, ignoreCase = true) == true)
                    }
                }
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (q.isEmpty() && recent.isNotEmpty()) {
                        ContinueReadingSection(
                            recent = recent,
                            onBook = onBook,
                            onRemove = { Graph.reading.removeProgress(it) },
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("作家を検索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        "作家 ${authors.size} 名 ・ 作品 ${c.books.size} 点",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(authors, key = { it.id }) { author ->
                            AuthorCard(author) { onAuthor(author.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingSection(
    recent: List<ProgressEntry>,
    onBook: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Text(
        "続きを読む",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(recent, key = { it.bookId }) { entry ->
            ContinueCard(entry, onClick = { onBook(entry.bookId) }, onRemove = { onRemove(entry.bookId) })
        }
    }
}

@Composable
private fun ContinueCard(entry: ProgressEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            Column(Modifier.padding(14.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { entry.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(entry.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            ) { Text("×", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun AuthorCard(author: Author, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuthorAvatar(name = author.name, seed = author.id)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    author.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val sub = listOfNotNull(author.kana, author.roman).joinToString(" ・ ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CountBadge(author.count)
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

/** 加载并记忆目录。 */
@Composable
fun rememberCatalog(): CatalogRepository.Catalog? {
    var state by remember { mutableStateOf<CatalogRepository.Catalog?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        state = Graph.catalog.load()
    }
    return state
}

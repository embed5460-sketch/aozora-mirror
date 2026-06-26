package app.meisaku.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.meisaku.reader.Graph
import app.meisaku.reader.ui.screen.AuthorBooksScreen
import app.meisaku.reader.ui.screen.LibraryScreen
import app.meisaku.reader.ui.screen.ReaderScreen
import app.meisaku.reader.ui.screen.SettingsScreen
import app.meisaku.reader.ui.theme.MeisakuTheme

object Routes {
    const val LIBRARY = "library"
    const val AUTHOR = "author/{authorId}"
    const val READER = "reader/{bookId}"
    const val SETTINGS = "settings"

    fun author(id: String) = "author/$id"
    fun reader(id: String) = "reader/$id"
}

@Composable
fun MeisakuApp() {
    val settings by Graph.settings.state
    MeisakuTheme(dark = settings.night) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onAuthor = { nav.navigate(Routes.author(it)) },
                    onBook = { nav.navigate(Routes.reader(it)) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.AUTHOR) { entry ->
                val authorId = entry.arguments?.getString("authorId").orEmpty()
                AuthorBooksScreen(
                    authorId = authorId,
                    onBook = { nav.navigate(Routes.reader(it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.READER) { entry ->
                val bookId = entry.arguments?.getString("bookId").orEmpty()
                ReaderScreen(
                    bookId = bookId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

package org.nekomanga.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.jetbrains.skia.Image
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.screens.BrowseScreen
import org.nekomanga.desktop.screens.DetailsScreen
import org.nekomanga.desktop.screens.HistoryScreen
import org.nekomanga.desktop.screens.LibraryScreen
import org.nekomanga.desktop.screens.ReaderScreen
import org.nekomanga.desktop.screens.SearchScreen
import org.nekomanga.desktop.state.AppContainer
import org.nekomanga.desktop.state.AppState
import org.nekomanga.desktop.state.LocalWindowControl
import org.nekomanga.desktop.state.Page
import org.nekomanga.desktop.state.WindowControl

/** Classpath path of the window icon (the same artwork as the Rust prototype's icon). */
const val APP_ICON = "neko-icon.png"

fun main() {
    val container = AppContainer()
    // Covers and pages load through the app's own HTTP client, so they carry Beako's User-Agent.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }
            .crossfade(true)
            .build()
    }
    application {
        val icon = remember { BitmapPainter(loadAppIcon()) }
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        val windowControl = remember { WindowControl(windowState) }
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            icon = icon,
            state = windowState,
        ) {
            val scope = rememberCoroutineScope()
            val state = remember { AppState(container, scope) }
            // Developer aid for screenshots: BEAKO_OPEN=search:<query>, title:<query> or
            // read:<query> starts on that page, using the first MangaDex match.
            LaunchedEffect(Unit) { openForDevelopment(state, System.getenv("BEAKO_OPEN")) }
            CompositionLocalProvider(LocalWindowControl provides windowControl) {
                BeakoApp(
                    start = startScreen(System.getenv("BEAKO_OPEN")),
                    navigator = state.navigator,
                    page = { AppPage(state, it) },
                    screen = { AppScreen(state, it) },
                )
            }
        }
    }
}

/** `BEAKO_OPEN=screen:library` (or updates, browse, history, more) starts on that screen. */
private fun startScreen(request: String?): Destination =
    request
        ?.removePrefix("screen:")
        ?.takeIf { request.startsWith("screen:") }
        ?.let { name ->
            Destination.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        } ?: Destination.BROWSE

private suspend fun openForDevelopment(state: AppState, request: String?) {
    val (kind, query) = request?.split(":", limit = 2)?.takeIf { it.size == 2 } ?: return
    if (kind == "screen") return
    if (kind == "search") {
        state.navigator.open(Page.Search(query))
        return
    }
    val title =
        runCatching { state.container.mangaDex.search(query, limit = 1).items.firstOrNull() }
            .getOrNull() ?: return
    state.navigator.open(Page.Details(title))
    if (kind == "read") {
        val chapters = runCatching {
            state.container.mangaDex.allChapters(title.id)
        }
            .getOrNull()
            ?.reversed()
            .orEmpty()
        val first = chapters.indexOfFirst { it.externalUrl == null && it.pages > 0 }
        if (first >= 0) state.navigator.open(Page.Reader(title, chapters, first))
    }
}

@Composable
private fun AppScreen(state: AppState, destination: Destination) {
    when (destination) {
        Destination.BROWSE ->
            BrowseScreen(
                state.browse,
                onSearch = { state.navigator.open(Page.Search()) },
                onOpen = { state.navigator.open(Page.Details(it)) },
            )
        Destination.LIBRARY ->
            LibraryScreen(
                state.libraryScreen,
                onOpen = { state.navigator.open(Page.Details(it)) },
                onSearchSources = { state.navigator.open(Page.Search(it)) },
                onBrowse = { state.navigator.open(Page.Search()) },
            )
        Destination.HISTORY ->
            HistoryScreen(
                state.container.library,
                onResume = { state.navigator.open(Page.Details(it.title)) },
                onBrowse = { state.navigator.open(Page.Search()) },
            )
        else -> PlaceholderScreen(destination)
    }
}

@Composable
private fun AppPage(state: AppState, page: Page) {
    when (page) {
        is Page.Search ->
            SearchScreen(
                state.search,
                page.query,
                onBack = { state.navigator.back() },
                onOpen = { state.navigator.open(Page.Details(it)) },
            )
        is Page.Details ->
            DetailsScreen(
                state.details(page.title),
                onBack = { state.navigator.back() },
                onRead = { chapters, index, start ->
                    state.navigator.open(Page.Reader(page.title, chapters, index, start))
                },
            )
        is Page.Reader -> ReaderScreen(state.reader(page), onClose = { state.navigator.back() })
    }
}

/** Decodes the bundled app icon. */
fun loadAppIcon(): ImageBitmap {
    val bytes =
        checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(APP_ICON)) {
                "Missing resource $APP_ICON"
            }
            .use { it.readBytes() }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

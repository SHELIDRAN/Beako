package org.nekomanga.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import org.nekomanga.desktop.components.AccountSlot
import org.nekomanga.desktop.components.Destination
import org.nekomanga.desktop.screens.AccountsSection
import org.nekomanga.desktop.screens.BackupSection
import org.nekomanga.desktop.screens.BrowseScreen
import org.nekomanga.desktop.screens.ComickReleasesShelf
import org.nekomanga.desktop.screens.DetailsScreen
import org.nekomanga.desktop.screens.ExtensionsSection
import org.nekomanga.desktop.screens.HistoryScreen
import org.nekomanga.desktop.screens.LibraryScreen
import org.nekomanga.desktop.screens.MoreScreen
import org.nekomanga.desktop.screens.ReaderScreen
import org.nekomanga.desktop.screens.SearchScreen
import org.nekomanga.desktop.screens.UpdatesScreen
import org.nekomanga.desktop.state.AppContainer
import org.nekomanga.desktop.state.AppState
import org.nekomanga.desktop.state.KeyBindings
import org.nekomanga.desktop.state.LocalWindowControl
import org.nekomanga.desktop.state.Page
import org.nekomanga.desktop.state.UpdateChecker
import org.nekomanga.desktop.state.WindowControl
import org.nekomanga.desktop.state.displayName
import org.nekomanga.desktop.theme.BeakoAppearance
import org.nekomanga.desktop.theme.Palette
import org.nekomanga.desktop.theme.SidebarMode
import org.nekomanga.shared.diagnostics.DiagnosticLog

/** Classpath path of the window icon (the same artwork as the Rust prototype's icon). */
const val APP_ICON = "neko-icon.png"

fun main(args: Array<String>) {
    // `Beako.exe --self-test <report>`: checks the bundled runtime and exits (used by CI).
    if (args.firstOrNull() == "--self-test") {
        kotlin.system.exitProcess(
            org.nekomanga.desktop.platform.SelfTest.runAndReport(
                args.getOrNull(1)?.let { java.io.File(it) },
            ),
        )
    }
    val container = AppContainer()
    DiagnosticLog.start(container.dirs.data)
    DiagnosticLog.info(
        "Beako ${System.getProperty("beako.version") ?: "dev"} started on ${System.getProperty("os.name")}",
    )
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        DiagnosticLog.error("Uncaught error on ${thread.name}", error)
    }
    // Covers and pages load through the app's own HTTP client, so they carry Beako's User-Agent.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient })) }
            .crossfade(true)
            .eventListener(ImageFailureLog)
            .build()
    }
    application {
        val icon = remember { BitmapPainter(loadAppIcon()) }
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        // A tray icon carries Windows notifications for new chapters.
        val trayState = rememberTrayState()
        if (isTraySupported) {
            Tray(
                icon = icon,
                state = trayState,
                tooltip = APP_NAME,
                onAction = { windowState.isMinimized = false },
            ) {
                Item("Open $APP_NAME", onClick = { windowState.isMinimized = false })
                Item(
                    "Quit",
                    onClick = {
                        container.library.flush()
                        exitApplication()
                    },
                )
            }
        }
        var awtFrame: java.awt.Frame? = null
        val windowControl = remember { WindowControl.forWindow(windowState) { awtFrame } }
        Window(
            onCloseRequest = {
                container.library.flush()
                container.extensions.closeAll()
                container.discord.close()
                exitApplication()
            },
            title = APP_NAME,
            icon = icon,
            state = windowState,
        ) {
            awtFrame = window
            val scope = rememberCoroutineScope()
            val state = remember {
                AppState(
                    container,
                    scope,
                    onUpdates = { updates ->
                        trayState.sendNotification(
                            Notification(
                                APP_NAME,
                                UpdateChecker.summary(updates),
                                Notification.Type.Info,
                            ),
                        )
                    },
                )
            }
            LaunchedEffect(state) {
                state.accounts.restore()
                // Only when the library loaded: a backup of an unreadable library would be empty.
                if (container.library.loadError == null) {
                    // Checked hourly, so a Beako left open for days still backs up once a day.
                    launch {
                        while (true) {
                            state.backups.runAutomatic()
                            kotlinx.coroutines.delay(60 * 60 * 1000L)
                        }
                    }
                }
                state.updates.start()
            }
            // Developer aid for screenshots: BEAKO_OPEN=search:<query>, title:<query> or
            // read:<query> starts on that page, using the first MangaDex match.
            LaunchedEffect(Unit) { openForDevelopment(state, System.getenv("BEAKO_OPEN")) }
            CompositionLocalProvider(LocalWindowControl provides windowControl) {
                val settings by container.settings.settings.collectAsState()
                BeakoApp(
                    appearance =
                        BeakoAppearance(
                            reduceMotion = settings.reduceMotion,
                            reduceTransparency = settings.reduceTransparency,
                            sidebarMode =
                                runCatching { SidebarMode.valueOf(settings.sidebarMode) }
                                    .getOrDefault(SidebarMode.AUTO_COLLAPSE),
                            palette = Palette.of(settings.palette),
                            glassStrength = settings.glassStrength,
                            scale = settings.uiScale,
                        ),
                    start = startScreen(System.getenv("BEAKO_OPEN")),
                    navigator = state.navigator,
                    page = { AppPage(state, it) },
                    screen = { AppScreen(state, it) },
                    accounts = accountSlots(state),
                    onAccountClick = { state.navigator.show(Destination.MORE) },
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
                state.libraryScreen.also {
                    it.downloadedOnly =
                        state.container.settings.settings.collectAsState().value.downloadedOnly
                },
                downloadChanges = state.container.downloads.changes.collectAsState().value,
                onOpen = { state.navigator.open(Page.Details(it)) },
                onSearchSources = { state.navigator.open(Page.Search(it)) },
                onBrowse = { state.navigator.show(Destination.BROWSE) },
            )
        Destination.UPDATES ->
            UpdatesScreen(
                state.container.library,
                state.updates,
                onOpen = { state.navigator.open(Page.Details(it)) },
                onLibrary = { state.navigator.show(Destination.LIBRARY) },
                shelf = {
                    ComickReleasesShelf(state.accounts) { state.navigator.open(Page.Details(it)) }
                },
            )
        Destination.HISTORY ->
            HistoryScreen(
                state.container.library,
                onResume = { state.navigator.open(Page.Details(it.title)) },
                onBrowse = { state.navigator.show(Destination.BROWSE) },
            )
        Destination.MORE ->
            MoreScreen(
                state.container.settings,
                state.container.library,
                state.container.downloads,
                state.container.dirs.downloads,
                accounts = {
                    AccountsSection(
                        state.accounts,
                        state.container.settings,
                        state.container.vault.persistent,
                    )
                },
                backup = { BackupSection(state.backups, state.container.settings) },
                sources = { ExtensionsSection(state.extensions) },
            )
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
                tracking = state.tracking(page.title),
                onOpenTitle = { state.navigator.open(Page.Details(it)) },
            )
        is Page.Reader -> {
            val settings by state.container.settings.settings.collectAsState()
            val reader = state.reader(page)
            // Discord shows the chapter while the reader is open, and nothing once it closes.
            val activity =
                if (!settings.discordPresence || settings.incognito) null
                else
                    org.nekomanga.desktop.platform.Activity(
                        details =
                            reader.title.title.takeIf { settings.discordShowTitle }
                                ?: "Reading manga",
                        state =
                            reader.chapter.displayName().takeIf {
                                settings.discordShowChapter && settings.discordShowTitle
                            },
                        startedAt = remember(reader.index) { System.currentTimeMillis() },
                        imageUrl =
                            reader.title.coverUrl.takeIf {
                                settings.discordShowCover && settings.discordShowTitle
                            },
                        imageText = reader.title.title.takeIf { settings.discordShowTitle },
                    )
            LaunchedEffect(activity) { state.container.discord.update(activity) }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { state.container.discord.update(null) }
            }
            ReaderScreen(
                state.reader(page),
                onClose = { state.navigator.back() },
                prefs = settings.reader,
                onPrefs = { prefs -> state.container.settings.update { it.copy(reader = prefs) } },
                keys = remember(settings.keyBindings) { KeyBindings(settings.keyBindings) },
            )
        }
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

/** The sidebar's account avatars: Comick and MangaDex always, AniList once connected. */
@Composable
private fun accountSlots(state: AppState): List<AccountSlot> {
    val comick by state.accounts.comick.account.collectAsState()
    val aniList by state.accounts.aniList.account.collectAsState()
    val mangaDex by state.accounts.mangaDex.username.collectAsState()
    return listOfNotNull(
        AccountSlot("Comick", comick?.name, comick?.avatarUrl),
        AccountSlot("MangaDex", mangaDex),
        aniList?.let { AccountSlot("AniList", it.name, it.avatarUrl) },
    )
}

/** Logs covers and pages that fail to load (HTTP errors, undecodable images) to beako.log. */
private object ImageFailureLog : coil3.EventListener() {
    // Each address once per session: a broken cover is shown, and failed, again and again.
    private val logged =
        java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
        )

    override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
        val address = request.data.toString()
        if (logged.size < 2_000 && logged.add(address)) {
            DiagnosticLog.error("Image failed to load: $address", result.throwable)
        }
    }
}

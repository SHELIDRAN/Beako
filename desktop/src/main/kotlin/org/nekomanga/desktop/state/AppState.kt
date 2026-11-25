package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import org.nekomanga.shared.library.LibraryEntry
import org.nekomanga.shared.model.Title

/** Everything that outlives a single screen: navigation and each screen's model. */
class AppState(
    val container: AppContainer,
    private val scope: CoroutineScope,
    onUpdates: (List<TitleUpdate>) -> Unit = {},
    openBrowser: (String) -> Unit = ::openInBrowser,
) {
    /** Windows' own animation and transparency settings, read now and then. */
    var systemComfort by
        androidx.compose.runtime.mutableStateOf(
            org.nekomanga.desktop.platform.SystemComfort(),
        )

    val navigator = Navigator()
    val extensions = ExtensionsModel(scope, container.extensions)
    val backups = BackupModel(scope, container.library, container.settings, container.dirs.backups)
    val accounts =
        AccountsModel(
            scope,
            container.comickAccount,
            container.comickTracker,
            container.aniList,
            container.mangaDexAccount,
            container.library,
            container.settings,
            openBrowser,
        )
    val browse = BrowseModel(scope, container.comick, container.mangaDex)
    val search = SearchModel(scope, container.discovery, container.comick)
    val mangaDexPage =
        MangaDexModel(
            scope,
            container.mangaDex,
            container.mangaDexAccount,
            languages = { container.settings.current.languages },
        )
    val libraryScreen =
        LibraryModel(
            container.library,
            downloadedKeys = { container.downloads.downloadedTitleKeys() },
            ignoreArticles = { container.settings.current.sortIgnoreArticles },
        )
    val updates =
        UpdateChecker(
            scope,
            container.library,
            container.mangaDex,
            container.discovery,
            intervalHours = { container.settings.current.updateIntervalHours },
            languages = { container.settings.current.languages },
            skip = { entry ->
                val s = container.settings.current
                UpdateChecker.skips(s.updateSkip, entry) ||
                    !UpdateChecker.inCategories(
                        s.updateCategories,
                        s.updateExcludedCategories,
                        entry,
                    )
            },
            extensionChapters = { source, url -> container.readingSources.chapters(source, url) },
            localChapters = {
                if (container.settings.current.localManga) container.local::chapters else null
            },
            canRunAutomatically = {
                !container.settings.current.updateOnlyOnPower ||
                    org.nekomanga.desktop.platform.Power.pluggedIn()
            },
            refreshDetails = { container.settings.current.refreshDetailsOnUpdate },
            onFound = onUpdates,
        )

    private val details = mutableMapOf<String, DetailsModel>()
    private var reader: Pair<Page.Reader, ReaderModel>? = null

    /** One model per title, kept so coming back from the reader doesn't reload the page. */
    fun details(title: Title): DetailsModel =
        details.getOrPut("${title.source}:${title.id}") {
            DetailsModel(
                scope,
                container.discovery,
                container.mangaDex,
                container.library,
                container.downloads,
                title,
                languages = { container.settings.current.languages },
                onChaptersRead = { chapters -> accounts.chaptersRead(title, chapters) },
                readingSources = container.readingSources,
                local = container.local.takeIf { container.settings.current.localManga },
                groupFilter = { container.settings.current.groupFilter },
                skipDuplicates = { container.settings.current.reader.skipDuplicates },
                dynamicCovers = { container.settings.current.dynamicCovers },
                defaultCategory = { container.settings.current.defaultCategory },
            )
        }

    private val tracking = mutableMapOf<String, TitleTracking>()

    fun tracking(title: Title): TitleTracking =
        tracking.getOrPut("${title.source}:${title.id}") {
            TitleTracking(scope, accounts, details(title))
        }

    fun reader(page: Page.Reader): ReaderModel {
        reader?.let { (current, model) -> if (current === page) return model }
        val key = LibraryEntry.keyOf(page.title)
        val model =
            ReaderModel(
                scope,
                container.mangaDex,
                page.title,
                page.chapters,
                page.index,
                container.library,
                page.page,
                incognito = { container.settings.current.incognito },
                localPages = { chapter -> container.downloads.localPages(page.title, chapter) },
                dataSaver = { container.settings.current.dataSaver },
                onChapterRead = { chapter -> accounts.chaptersRead(page.title, listOf(chapter)) },
                savedMode =
                    container.settings.current.reader.titleModes[key]?.let { name ->
                        ReadingMode.entries.firstOrNull { it.name == name }
                    },
                saveMode = { mode ->
                    container.settings.update {
                        it.copy(
                            reader =
                                it.reader.copy(
                                    titleModes = it.reader.titleModes + (key to mode.name),
                                ),
                        )
                    }
                },
                autoWebtoon = { container.settings.current.reader.autoWebtoon },
                splitWidePages = { container.settings.current.reader.splitWidePages },
                pageSource = { chapter ->
                    if (chapter.source == org.nekomanga.shared.model.Sources.LOCAL) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            org.nekomanga.shared.model.PageList(
                                container.local.pages(chapter).map { LOCAL_PAGE + it.absolutePath },
                            )
                        }
                    } else container.readingSources.pages(chapter)
                },
                defaultMode =
                    container.settings.current.reader.defaultMode.let { name ->
                        ReadingMode.entries.firstOrNull { it.name == name }
                    },
                isRead = { chapter ->
                    chapter.id in container.library.entry(page.title)?.readChapterIds.orEmpty()
                },
                skipRead = { container.settings.current.reader.skipRead },
                transitions = { container.settings.current.reader.chapterTransition },
            )
        reader = page to model
        return model
    }
}

/** Opens a web page in the system browser. */
fun openInBrowser(url: String) {
    runCatching {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        }
    }
}

package org.nekomanga.desktop.state

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
    val libraryScreen =
        LibraryModel(
            container.library,
            downloadedKeys = { container.downloads.downloadedTitleKeys() },
        )
    val updates =
        UpdateChecker(
            scope,
            container.library,
            container.mangaDex,
            container.discovery,
            intervalHours = { container.settings.current.updateIntervalHours },
            languages = { container.settings.current.languages },
            skip = { entry -> UpdateChecker.skips(container.settings.current.updateSkip, entry) },
            extensionChapters = { source, url -> container.readingSources.chapters(source, url) },
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
                pageSource = { chapter -> container.readingSources.pages(chapter) },
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

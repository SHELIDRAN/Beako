package org.nekomanga.desktop.state

import kotlinx.coroutines.CoroutineScope
import org.nekomanga.shared.model.Title

/** Everything that outlives a single screen: navigation and each screen's model. */
class AppState(val container: AppContainer, private val scope: CoroutineScope) {
    val navigator = Navigator()
    val browse = BrowseModel(scope, container.comick, container.mangaDex)
    val search = SearchModel(scope, container.discovery, container.comick)
    val libraryScreen = LibraryModel(container.library)

    private val details = mutableMapOf<String, DetailsModel>()
    private var reader: Pair<Page.Reader, ReaderModel>? = null

    /** One model per title, kept so coming back from the reader doesn't reload the page. */
    fun details(title: Title): DetailsModel =
        details.getOrPut("${title.source}:${title.id}") {
            DetailsModel(scope, container.discovery, container.mangaDex, container.library, title)
        }

    fun reader(page: Page.Reader): ReaderModel {
        reader?.let { (current, model) -> if (current === page) return model }
        val model =
            ReaderModel(
                scope,
                container.mangaDex,
                page.title,
                page.chapters,
                page.index,
                container.library,
                page.page,
            )
        reader = page to model
        return model
    }
}

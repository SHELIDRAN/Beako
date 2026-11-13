package org.nekomanga.desktop.state

import kotlinx.coroutines.CoroutineScope

/** Everything that outlives a single screen: navigation and each screen's model. */
class AppState(val container: AppContainer, scope: CoroutineScope) {
    val navigator = Navigator()
    val browse = BrowseModel(scope, container.comick, container.mangaDex)
    val search = SearchModel(scope, container.discovery, container.comick)
}

package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.nekomanga.shared.discovery.Discovery
import org.nekomanga.shared.mangadex.MangaDexApi
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError

/**
 * State of a title page. The title can come from Comick or MangaDex; its chapters come from the
 * reading sources that carry it. MangaDex is the only reading source until extensions land.
 */
class DetailsModel(
    private val scope: CoroutineScope,
    private val discovery: Discovery,
    private val mangaDex: MangaDexApi,
    val title: Title,
) {
    /** Chapters newest first, as the list shows them. */
    var chapters by mutableStateOf<Loadable<List<Chapter>>>(Loadable.Loading)
        private set

    /** Names of the reading sources that have this title; empty until the lookup finishes. */
    var availableOn by mutableStateOf<List<String>>(emptyList())
        private set

    private var started = false

    fun start() {
        if (!started) {
            started = true
            load()
        }
    }

    fun retry() = load()

    /** [chapters] oldest first, the order the reader moves through them. */
    fun readingOrder(): List<Chapter> = (chapters as? Loadable.Ready)?.value.orEmpty().reversed()

    private fun load() {
        chapters = Loadable.Loading
        scope.launch {
            chapters =
                try {
                    val onMangaDex = discovery.findOnMangaDex(title)
                    if (onMangaDex == null) {
                        availableOn = emptyList()
                        Loadable.Ready(emptyList())
                    } else {
                        availableOn = listOf(MangaDexApi.SERVICE)
                        Loadable.Ready(mangaDex.allChapters(onMangaDex.id))
                    }
                } catch (e: ApiError) {
                    Loadable.Failed(e.message.orEmpty())
                }
        }
    }
}

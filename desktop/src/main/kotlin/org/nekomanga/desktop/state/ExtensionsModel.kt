package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.nekomanga.shared.extensions.ExtensionInfo
import org.nekomanga.shared.extensions.ExtensionManager

/** More → Sources: repositories, the sources they offer, and installed sources. */
class ExtensionsModel(private val scope: CoroutineScope, val manager: ExtensionManager) {

    /** Each repository's sources, loaded when the section opens. */
    val catalog = mutableStateMapOf<String, Loadable<List<ExtensionInfo>>>()

    var message by mutableStateOf<String?>(null)
        private set

    var isError by mutableStateOf(false)
        private set

    /** Source ids being installed or updated. */
    val busy = mutableStateMapOf<Long, Boolean>()

    /** Installed sources with a newer version, by id. */
    var updates by mutableStateOf<Map<Long, Pair<ExtensionInfo, String>>>(emptyMap())
        private set

    private var loaded = false

    fun start() {
        if (loaded) return
        loaded = true
        refresh()
    }

    fun refresh() {
        manager.data.value.repositories.forEach(::loadRepository)
        scope.launch {
            updates =
                runCatching { manager.updatesAvailable() }
                    .getOrDefault(emptyList())
                    .associateBy { it.first.id }
        }
    }

    private fun loadRepository(url: String) {
        catalog[url] = Loadable.Loading
        scope.launch {
            catalog[url] =
                try {
                    Loadable.Ready(manager.index(url))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Loadable.Failed(e.message ?: "Couldn’t load this repository.")
                }
        }
    }

    fun addRepository(pasted: String) = work {
        val sources = manager.addRepository(pasted)
        manager.data.value.repositories.lastOrNull()?.let { catalog[it] = Loadable.Ready(sources) }
        val usable = sources.count { it.isJavaScript }
        "Added a repository with ${sources.size} sources ($usable Beako can run now)."
    }

    fun removeRepository(url: String) {
        manager.removeRepository(url)
        catalog.remove(url)
    }

    fun install(info: ExtensionInfo, repository: String) {
        busy[info.id] = true
        work({ busy.remove(info.id) }) {
            manager.install(info, repository)
            updates = updates - info.id
            "Installed ${info.name}."
        }
    }

    fun uninstall(id: Long) {
        val name = manager.installed.firstOrNull { it.info.id == id }?.info?.name
        manager.uninstall(id)
        report("Removed ${name ?: "the source"}.", error = false)
    }

    private fun work(after: () -> Unit = {}, block: suspend () -> String) {
        scope.launch {
            try {
                report(block(), error = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                report(e.message ?: "Something went wrong.", error = true)
            } catch (e: Exception) {
                report("Something went wrong: ${e.message}", error = true)
            } finally {
                after()
            }
        }
    }

    private fun report(text: String, error: Boolean) {
        message = text
        isError = error
    }
}

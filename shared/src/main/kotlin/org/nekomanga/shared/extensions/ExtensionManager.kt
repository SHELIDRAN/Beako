package org.nekomanga.shared.extensions

import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.nekomanga.shared.library.readWithRetry
import org.nekomanga.shared.library.writeAtomically
import org.nekomanga.shared.net.BeakoJson
import org.nekomanga.shared.net.HttpFetcher

@Serializable
data class ExtensionsData(
    val repositories: List<String> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val showNsfw: Boolean = false,
)

/**
 * Installed Mangayomi sources and the repositories they come from. Beako ships none: the user adds
 * a repository, then installs sources one by one. Code lives in [folder] as `<id>.js`; nothing runs
 * until a source is used.
 */
class ExtensionManager(
    private val folder: File,
    private val http: HttpFetcher,
    private val transport: ExtensionTransport,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val file = File(folder, "extensions.json")
    private val state = MutableStateFlow(load())
    private val running = HashMap<Long, JsSource>()

    val data: StateFlow<ExtensionsData> = state.asStateFlow()

    val installed: List<InstalledExtension>
        get() = state.value.installed

    /** Sources Beako can run now: installed, enabled JavaScript ones. */
    val usable: List<InstalledExtension>
        get() = installed.filter { it.enabled && it.info.isJavaScript }

    /**
     * Adds a repository from an index URL or a `mangayomi://add-repo` link; returns its sources.
     */
    suspend fun addRepository(pasted: String): List<ExtensionInfo> {
        val url =
            Repositories.indexUrl(pasted)
                ?: throw IOException(
                    "Paste an https link to a repository’s index.json, or a Mangayomi add-repo link.",
                )
        policy(url)
        val sources = index(url)
        update { it.copy(repositories = (it.repositories + url).distinct()) }
        return sources
    }

    fun removeRepository(url: String) = update { it.copy(repositories = it.repositories - url) }

    fun setShowNsfw(show: Boolean) = update { it.copy(showNsfw = show) }

    /** The sources a repository lists (manga only). */
    suspend fun index(url: String): List<ExtensionInfo> {
        val response = http.get(url)
        if (response.code !in 200..299)
            throw IOException("The repository answered ${response.code}.")
        return Repositories.parseIndex(response.body)
    }

    /**
     * Downloads and installs [info]'s code. JavaScript only for now; refuses anything that would
     * read from Comick.
     */
    suspend fun install(info: ExtensionInfo, repositoryUrl: String) {
        if (!info.isJavaScript)
            throw IOException(
                "${info.name} is a Dart source. Beako can run JavaScript sources only, for now.",
            )
        if (HostPolicy.isComick(hostOf(info.baseUrl)))
            throw IOException("Beako doesn’t read from Comick.")
        if (Repositories.indexUrl(info.sourceCodeUrl) == null)
            throw IOException("${info.name} has no https download link.")
        policy(info.sourceCodeUrl)
        val response = http.get(info.sourceCodeUrl)
        if (response.code !in 200..299 || response.body.isBlank()) {
            throw IOException("Couldn’t download ${info.name} (${response.code}).")
        }
        folder.mkdirs()
        writeAtomically(codeFile(info.id), response.body)
        close(info.id)
        update { data ->
            val previous = data.installed.firstOrNull { it.info.id == info.id }
            val entry =
                InstalledExtension(
                    info = info,
                    repositoryUrl = repositoryUrl,
                    installedAt = now(),
                    enabled = previous?.enabled ?: true,
                    preferences = previous?.preferences.orEmpty(),
                )
            data.copy(installed = data.installed.filterNot { it.info.id == info.id } + entry)
        }
    }

    fun uninstall(id: Long) {
        close(id)
        codeFile(id).delete()
        update { data -> data.copy(installed = data.installed.filterNot { it.info.id == id }) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        if (!enabled) close(id)
        update { data ->
            data.copy(
                installed =
                    data.installed.map { if (it.info.id == id) it.copy(enabled = enabled) else it },
            )
        }
    }

    fun setPreference(id: Long, key: String, value: String) = update { data ->
        data.copy(
            installed =
                data.installed.map {
                    if (it.info.id == id) it.copy(preferences = it.preferences + (key to value))
                    else it
                },
        )
    }

    /** Installed sources with a newer version in their repository's index. */
    suspend fun updatesAvailable(): List<Pair<ExtensionInfo, String>> {
        val byRepo = installed.groupBy { it.repositoryUrl }
        return byRepo.flatMap { (repo, sources) ->
            val index = runCatching { index(repo) }.getOrDefault(emptyList()).associateBy { it.id }
            sources.mapNotNull { mine ->
                index[mine.info.id]
                    ?.takeIf { Repositories.isNewer(it.version, mine.info.version) }
                    ?.let { it to repo }
            }
        }
    }

    /** The running source for [id], started on first use. */
    @Synchronized
    fun source(id: Long): JsSource {
        running[id]?.let {
            return it
        }
        val entry =
            installed.firstOrNull { it.info.id == id }
                ?: throw IOException("That source isn’t installed.")
        val code =
            runCatching { codeFile(id).readText() }.getOrNull()
                ?: throw IOException("${entry.info.name} needs reinstalling.")
        return JsSource(
                entry.info,
                code,
                transport,
                preferences = { installed.firstOrNull { it.info.id == id }?.preferences.orEmpty() },
                onPreferenceSet = { key, value -> setPreference(id, key, value) },
            )
            .also { running[id] = it }
    }

    @Synchronized
    private fun close(id: Long) {
        running.remove(id)?.close()
    }

    @Synchronized
    fun closeAll() {
        running.values.forEach { it.close() }
        running.clear()
    }

    private fun codeFile(id: Long) = File(folder, "$id.js")

    /** Repositories and code downloads follow the same host rules as the scripts. */
    private fun policy(url: String) {
        try {
            HostPolicy.check(url)
        } catch (e: HostPolicy.Blocked) {
            throw IOException(e.message)
        }
    }

    private fun hostOf(url: String) = runCatching {
        java.net.URI(url).host.orEmpty().lowercase()
    }
        .getOrDefault("")

    @Synchronized
    private fun update(change: (ExtensionsData) -> ExtensionsData) {
        val next = change(state.value)
        if (next == state.value) return
        state.value = next
        runCatching {
            writeAtomically(file, BeakoJson.encodeToString(ExtensionsData.serializer(), next))
        }
    }

    private fun load(): ExtensionsData =
        runCatching {
            readWithRetry(file)?.let { BeakoJson.decodeFromString(ExtensionsData.serializer(), it) }
        }
            .getOrNull() ?: ExtensionsData()
}

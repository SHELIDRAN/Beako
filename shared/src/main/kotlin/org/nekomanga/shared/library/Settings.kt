package org.nekomanga.shared.library

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.nekomanga.shared.net.BeakoJson

@Serializable
data class Settings(
    /** Pauses reading history and progress. */
    val incognito: Boolean = false,
    /** Shows only titles and chapters that are downloaded. */
    val downloadedOnly: Boolean = false,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    /** "AUTO_COLLAPSE", "ALWAYS_EXPANDED" or "ICONS_ONLY". */
    val sidebarMode: String = "AUTO_COLLAPSE",
    /** Hours between library update checks; 0 turns them off. */
    val updateIntervalHours: Int = 6,
    /** Download MangaDex's smaller, compressed images. */
    val dataSaver: Boolean = false,
    /** Chapter languages, as MangaDex codes. */
    val languages: List<String> = listOf("en"),
    /** The user's own AniList API client (its redirect URL must be Beako's callback). */
    val aniListClientId: String = "",
    /** MangaDex personal API client ID and username; the secret lives in the vault. */
    val mangaDexClientId: String = "",
    val mangaDexUsername: String = "",
    /** Mark chapters read on MangaDex as they're read in Beako. */
    val syncReadToMangaDex: Boolean = true,
    /** Push reading progress to linked trackers as chapters are finished. */
    val autoTrack: Boolean = true,
)

/** App settings in a small JSON file, saved atomically on every change. */
class SettingsStore(private val file: File) {
    private val state =
        MutableStateFlow(
            runCatching {
                readWithRetry(file)?.let { BeakoJson.decodeFromString(Settings.serializer(), it) }
            }
                .getOrNull() ?: Settings(),
        )

    val settings: StateFlow<Settings> = state.asStateFlow()

    val current: Settings
        get() = state.value

    @Synchronized
    fun update(change: (Settings) -> Settings) {
        val next = change(state.value)
        if (next == state.value) return
        state.value = next
        try {
            writeAtomically(file, BeakoJson.encodeToString(Settings.serializer(), next))
        } catch (e: java.io.IOException) {
            // The setting applies for this session; the next change tries to save again.
        }
    }
}

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
    /** Colour scheme name ("NEUTRAL", "MIDNIGHT", "WARM", "FOREST", "OLED"). */
    val palette: String = "NEUTRAL",
    /** Glass blur and tint strength, 0.5–1.5. */
    val glassStrength: Float = 1f,
    /** Interface size, 0.8–1.5. */
    val uiScale: Float = 1f,
    /** Hours between library update checks; 0 turns them off. */
    val updateIntervalHours: Int = 6,
    /**
     * Titles the update check skips: "COMPLETED" (finished and fully read), "UNREAD",
     * "NOT_STARTED".
     */
    val updateSkip: Set<String> = emptySet(),
    /**
     * "" automatic (DNS over HTTPS, then the system's), "SYSTEM", or a provider ("CLOUDFLARE",
     * "GOOGLE", "QUAD9").
     */
    val dnsOverHttps: String = "",
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
    val reader: ReaderPrefs = ReaderPrefs(),
    /** Reader key overrides: action name to AWT key codes. Missing actions use the defaults. */
    val keyBindings: Map<String, List<Int>> = emptyMap(),
    /** Show what you're reading in Discord (never in incognito mode). */
    val discordPresence: Boolean = false,
    val discordShowTitle: Boolean = true,
    val discordShowChapter: Boolean = true,
    val discordShowCover: Boolean = true,
    /** Discord application id; empty uses Beako's own. */
    val discordAppId: String = "",
    /** Write a backup once a day, keeping a week of them. */
    val autoBackup: Boolean = true,
)

/** How the reader lays out pages. */
@Serializable
data class ReaderPrefs(
    /** Webtoon strip width, as a percentage of the window (30–100). */
    val webtoonWidth: Int = 62,
    /** Space between webtoon pages, in dp (0–32). */
    val pageGap: Int = 0,
    /** Paged modes: "SCREEN", "WIDTH", "HEIGHT" or "ORIGINAL". */
    val fit: String = "SCREEN",
    /** "BLACK", "GRAY" or "WHITE". */
    val background: String = "BLACK",
    /** Swap the left and right click zones in paged modes. */
    val invertClickZones: Boolean = false,
    /** Paged modes: show double-page spreads one half at a time. */
    val splitWidePages: Boolean = false,
    /** Switch to webtoon mode when the pages are long strips. */
    val autoWebtoon: Boolean = true,
    /** Reading mode chosen for a title, by library key. */
    val titleModes: Map<String, String> = emptyMap(),
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

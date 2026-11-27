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
    /** How lively motion feels: "CALM", "BALANCED" or "LIVELY" (see MotionFeel). */
    val motionFeel: String = "BALANCED",
    val reduceTransparency: Boolean = false,
    /** "AUTO_COLLAPSE", "ALWAYS_EXPANDED" or "ICONS_ONLY". */
    val sidebarMode: String = "AUTO_COLLAPSE",
    /** The colour scheme in use (a Palette name, e.g. "NEUTRAL", "NORD_DARK"). */
    val palette: String = "NEUTRAL",
    /** The light and dark schemes "Follow Windows light or dark mode" switches between. */
    val lightPalette: String = "PORCELAIN",
    val darkPalette: String = "NEUTRAL",
    /** Use [lightPalette] or [darkPalette] to match Windows' app mode. */
    val followSystemTheme: Boolean = false,
    /** Look for a newer Beako on GitHub after start and twice a day. */
    val checkAppUpdates: Boolean = true,
    /** "Later" on the update notice: that version stays quiet until [appUpdateSnoozedUntil]. */
    val appUpdateSnoozedVersion: String = "",
    val appUpdateSnoozedUntil: Long = 0,
    /** Library groups folded away, as "<grouping>:<group name>" (e.g. "CATEGORY:Reading"). */
    val collapsedGroups: Set<String> = emptySet(),
    /** Search with tags' filters (without the keyword), kept between launches. */
    val searchFilters: org.nekomanga.shared.comick.ComickFilters? = null,
    /** Named filter sets saved from Search with tags; also listed on Browse. */
    val searchPresets: List<SearchPreset> = emptyList(),
    /** Where the window was and how big, for the next start; null until it has been moved. */
    val window: WindowBounds? = null,
    /** Open Beako in full screen (F11 switches). */
    val startFullscreen: Boolean = true,
    /** Windows 11's Mica material in the window frame (with a title bar matching the palette). */
    val windowBackdrop: Boolean = true,
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

    // Title page
    /** Tint the title page's buttons and accents with the cover's colour. */
    val themeFromCover: Boolean = true,
    /** Show the cover of the volume you're reading (MangaDex titles). */
    val dynamicCovers: Boolean = true,
    /** The blurred cover behind the title page's header: "OFF", "SMALL", "DEFAULT", "LARGE". */
    val backdrop: String = "DEFAULT",
    /** Title page buttons as icons only (names show on hover). */
    val hideButtonText: Boolean = false,
    /** Show every alternative title, wrapped, instead of one scrollable line. */
    val wrapAltTitles: Boolean = false,

    // Library
    /** Sort by title ignoring a leading "a", "an" or "the". */
    val sortIgnoreArticles: Boolean = true,
    /**
     * Hidden scanlator groups hide a chapter shared by several groups when "ANY" of them is hidden,
     * or only when "ALL" of them are.
     */
    val groupFilter: String = "ANY",
    /** Category for titles added to the library: null asks, "" adds none, else that category. */
    val defaultCategory: String? = null,
    /** Read manga from a folder on this PC. */
    val localManga: Boolean = true,
    /** The local manga folder; empty uses Documents\\Beako\\Local. */
    val localFolder: String = "",

    // Library updates
    /** Automatic update checks only while the PC is plugged in. */
    val updateOnlyOnPower: Boolean = false,
    /** Refresh titles' details and covers (MangaDex) during update checks. */
    val refreshDetailsOnUpdate: Boolean = false,
    /** Categories the check covers (empty: all) and leaves out. */
    val updateCategories: Set<String> = emptySet(),
    val updateExcludedCategories: Set<String> = emptySet(),
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
    /** Mode for titles you haven't set one for: "AUTO" (by type) or a ReadingMode name. */
    val defaultMode: String = "AUTO",
    /** Pages loaded ahead of the one you're on (1–10). */
    val preload: Int = 3,
    /** Open the reader in full screen. */
    val openFullscreen: Boolean = false,
    /** Keep the display awake while reading. */
    val keepScreenOn: Boolean = true,
    /** A small page number while the controls are hidden. */
    val showPageNumber: Boolean = true,
    /** Show pages in grey (easier on some displays and for black-and-white comics). */
    val grayscale: Boolean = false,
    /** Trim plain white or black margins around pages. */
    val cropBorders: Boolean = false,
    /** Next chapter skips chapters already read. */
    val skipRead: Boolean = false,
    /** When a chapter is uploaded by several groups, read just one. */
    val skipDuplicates: Boolean = false,
    /** Paged modes: a page between chapters saying what you finished and what's next. */
    val chapterTransition: Boolean = true,
    /** Paged modes: slide between pages. */
    val animateTurns: Boolean = true,
    /**
     * Paged modes: "SINGLE", "DOUBLE", or "AUTO" (two pages when the window is wider than tall).
     */
    val pageLayout: String = "SINGLE",
    /** Two-page layout: swap which page is on the left. */
    val invertDouble: Boolean = false,
    /** Two-page layout: space between the pages, in dp (0–48). */
    val doubleGap: Int = 0,
    /** Click zones: "DEFAULT", "KINDLE", "EDGE", "L_SHAPE" or "DISABLED". */
    val tapZones: String = "DEFAULT",
    /** Webtoon: how readily scrolling hides the controls: "LOW", "MEDIUM", "HIGH". */
    val hideOnScroll: String = "MEDIUM",
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

/**
 * The window's last normal (not maximised) position and size in dp, and whether it was maximised.
 */
@Serializable
data class WindowBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val maximized: Boolean = true,
)

/** A named set of Search with tags filters. */
@Serializable
data class SearchPreset(val name: String, val filters: org.nekomanga.shared.comick.ComickFilters)

/** [presets] with [preset] added, replacing one of the same name (ignoring case). */
fun withPreset(presets: List<SearchPreset>, preset: SearchPreset): List<SearchPreset> =
    presets.filterNot { it.name.equals(preset.name, ignoreCase = true) } + preset

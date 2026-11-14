package org.nekomanga.shared.library

import java.io.File

/**
 * Where Beako keeps its files. On Windows: data in `%APPDATA%\Beako`, cache in
 * `%LOCALAPPDATA%\Beako\cache`, downloads in `Documents\Beako`. `BEAKO_HOME` overrides all three
 * (for tests and portable use).
 */
class AppDirs(
    env: (String) -> String? = System::getenv,
    home: String = System.getProperty("user.home"),
) {
    private val override = env("BEAKO_HOME")?.let(::File)
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    val data: File =
        override?.resolve("data")
            ?: env("APPDATA")?.takeIf { windows }?.let { File(it, "Beako") }
            ?: File(env("XDG_DATA_HOME") ?: "$home/.local/share", "Beako")

    val cache: File =
        override?.resolve("cache")
            ?: env("LOCALAPPDATA")?.takeIf { windows }?.let { File(it, "Beako/cache") }
            ?: File(env("XDG_CACHE_HOME") ?: "$home/.cache", "Beako")

    val downloads: File = override?.resolve("downloads") ?: File(home, "Documents/Beako")

    val libraryFile: File
        get() = data.resolve("library.json")

    val settingsFile: File
        get() = data.resolve("settings.json")

    /** Automatic daily backups. */
    val backups: File
        get() = data.resolve("backups")
}

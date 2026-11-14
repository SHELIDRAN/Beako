package org.nekomanga.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nekomanga.shared.backup.Backups
import org.nekomanga.shared.backup.BeakoBackup
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.SettingsStore

/** Creating and restoring backups, and the daily automatic one. */
class BackupModel(
    private val scope: CoroutineScope,
    private val library: LibraryStore,
    private val settings: SettingsStore,
    val folder: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The result of the last backup or restore, for the More screen. */
    var message by mutableStateOf<String?>(null)
        private set

    var isError by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    private fun snapshot() =
        BeakoBackup(createdAt = now(), library = library.data.value, settings = settings.current)

    /** Today's automatic backup, when turned on and not yet written. */
    fun runAutomatic(today: LocalDate = LocalDate.now()) {
        if (!settings.current.autoBackup) return
        scope.launch(Dispatchers.IO) {
            try {
                Backups.automatic(folder, ::snapshot, today)
            } catch (e: IOException) {
                report("The automatic backup couldn’t be saved: ${e.message}", error = true)
            }
        }
    }

    fun create(file: File) = work {
        Backups.write(file, snapshot())
        "Backup saved to ${file.name}."
    }

    /**
     * Restores [file] (a Beako or Android Neko backup) into the library. Titles already here keep
     * their progress; a Beako backup also brings back its settings, except account details.
     */
    fun restore(file: File) = work {
        val (incoming, restoredSettings) = Backups.read(file)
        val added = library.merge(incoming)
        restoredSettings?.let { restored ->
            settings.update { current ->
                restored.copy(
                    aniListClientId = current.aniListClientId,
                    mangaDexClientId = current.mangaDexClientId,
                    mangaDexUsername = current.mangaDexUsername,
                )
            }
        }
        val count = incoming.entries.size
        buildString {
            append("Restored $count ${if (count == 1) "title" else "titles"}")
            append(if (added == count) "." else " ($added new).")
            if (restoredSettings != null) append(" Settings restored too.")
        }
    }

    private fun work(block: () -> String) {
        busy = true
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { block() }
                report(text, error = false)
            } catch (e: IOException) {
                report(e.message ?: "Couldn’t read or write the backup.", error = true)
            } finally {
                busy = false
            }
        }
    }

    private fun report(text: String, error: Boolean) {
        message = text
        isError = error
    }
}

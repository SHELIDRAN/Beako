package org.nekomanga.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.nekomanga.desktop.state.BackupModel
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.SettingsStore
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

@OptIn(ExperimentalCoroutinesApi::class)
class BackupModelTest {

    @Test
    fun restoringBringsBackTitlesAndSettingsButKeepsThisPcsAccounts() = runTest {
        val dir = Files.createTempDirectory("beako").toFile()
        val title = Title(Sources.MANGADEX, "m", "Sample")
        val oldLibrary = LibraryStore(File(dir, "old.json")).apply { add(title) }
        val oldSettings =
            SettingsStore(File(dir, "old-settings.json")).apply {
                update { it.copy(dataSaver = true, mangaDexUsername = "old-user") }
            }
        val file = File(dir, "b.beako.json.gz")
        val writer = BackupModel(this, oldLibrary, oldSettings, File(dir, "auto"))
        writer.create(file)
        // File work runs on the IO dispatcher; wait for it.
        waitUntil { !writer.busy }

        val library = LibraryStore(File(dir, "new.json"))
        val settings =
            SettingsStore(File(dir, "new-settings.json")).apply {
                update { it.copy(mangaDexUsername = "this-pc") }
            }
        val model = BackupModel(this, library, settings, File(dir, "auto"))
        model.restore(file)
        waitUntil { !model.busy }

        assertEquals(listOf(title), library.data.value.entries.map { it.title })
        assertEquals(true, settings.current.dataSaver)
        assertEquals("this-pc", settings.current.mangaDexUsername)
        assertEquals("Restored 1 title. Settings restored too.", model.message)
    }

    private fun kotlinx.coroutines.test.TestScope.waitUntil(done: () -> Boolean) {
        repeat(200) {
            testScheduler.advanceUntilIdle()
            if (done()) return
            Thread.sleep(10)
        }
    }
}

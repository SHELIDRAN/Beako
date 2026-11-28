package org.nekomanga.shared

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

class ReadStateTest {
    private var clock = 1L

    private fun store() =
        LibraryStore(File(Files.createTempDirectory("beako").toFile(), "library.json")) { clock++ }

    private val title = Title(Sources.MANGADEX, "t", "Moto Oshiego")

    private fun chapter(n: String) =
        Chapter(Sources.MANGADEX, "c$n", n, language = "en", publishedAt = "2026-11-14")

    @Test
    fun titlesOutsideTheLibraryRememberWhatWasRead() {
        val store = store()
        store.recordProgress(title, chapter("3"), 8)
        store.setRead(title, listOf("c1", "c2"), true)
        val state = store.readState(title)
        assertEquals("c3", state.progress!!.chapterId)
        assertEquals(8, state.progress!!.page)
        assertEquals(setOf("c1", "c2"), state.readChapterIds)
        assertTrue(store.entry(title) == null, "still not in the library")
    }

    @Test
    fun addingKeepsItAndRemovingDoesNotLoseIt() {
        val store = store()
        store.recordProgress(title, chapter("3"), 8)
        store.setRead(title, listOf("c1"), true)
        store.add(title)
        assertEquals(setOf("c1"), store.entry(title)!!.readChapterIds)
        assertEquals("c3", store.entry(title)!!.progress!!.chapterId)
        store.setRead(title, listOf("c2"), true)
        store.remove(title)
        assertEquals(setOf("c1", "c2"), store.readState(title).readChapterIds)
    }

    @Test
    fun historyFromBeforeStillGivesThePlace() {
        val store = store()
        // As if read on an older Beako: a history entry, nothing else.
        store.recordProgress(title, chapter("3"), 8)
        val data = store.data.value
        store.replaceForTest(data.copy(reading = emptyMap()))
        assertEquals("c3", store.readState(title).progress!!.chapterId)
        assertEquals(8, store.readState(title).progress!!.page)
        assertNull(store.readState(Title(Sources.MANGADEX, "other", "Other")).progress)
    }
}

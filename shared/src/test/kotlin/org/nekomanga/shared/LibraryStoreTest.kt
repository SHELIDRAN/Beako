package org.nekomanga.shared

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.nekomanga.shared.library.LibraryStore
import org.nekomanga.shared.library.ReadingStatus
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

class LibraryStoreTest {

    private val dir: File = Files.createTempDirectory("beako-lib").toFile()
    private val file = File(dir, "library.json")
    private var clock = 1_000L

    private fun store() = LibraryStore(file) { clock }

    private val title = Title(Sources.COMICK, "hid1", "Sample")

    private fun chapter(n: Int) =
        Chapter(
            Sources.MANGADEX,
            "c$n",
            "$n",
            language = "en",
            publishedAt = "2026-01-0${n}",
            pages = 10,
        )

    @Test
    fun addedTitlesAndCategoriesSurviveARestart() {
        store().add(title, categories = listOf("Romance", "Romance"), mangaDexId = "md1")

        val reopened = store()
        val entry = assertNotNull(reopened.entry(title))
        assertEquals(listOf("Romance"), entry.categories)
        assertEquals("md1", entry.mangaDexId)
        assertEquals(listOf("Romance"), reopened.data.value.categories)
        assertEquals(ReadingStatus.NOT_STARTED, entry.status)
    }

    @Test
    fun progressAndReadChaptersDriveStatusAndHistory() {
        val store = store()
        store.add(title)
        store.recordCheck("comick:hid1", listOf(chapter(1), chapter(2)))

        clock = 2_000
        store.recordProgress(title, chapter(1), page = 4)
        assertEquals(ReadingStatus.READING, store.entry(title)!!.status)
        assertEquals("c1", store.entry(title)!!.progress!!.chapterId)
        assertEquals(1, store.data.value.history.size)

        store.setRead(title, listOf("c1", "c2"), read = true)
        assertEquals(ReadingStatus.COMPLETED, store.entry(title)!!.status)
        assertEquals(0, store.entry(title)!!.unreadCount)

        // Reading again replaces the title's history entry instead of adding another.
        store.recordProgress(title, chapter(2), page = 0)
        assertEquals(1, store.data.value.history.size)
        assertEquals("c2", store.data.value.history.first().chapter.id)
    }

    @Test
    fun incognitoRecordsNothing() {
        val store = store()
        store.add(title)
        store.recordProgress(title, chapter(1), page = 3, incognito = true)
        assertNull(store.entry(title)!!.progress)
        assertTrue(store.data.value.history.isEmpty())
    }

    @Test
    fun theFirstCheckRecordsChaptersAndLaterChecksFindNewOnes() {
        val store = store()
        store.add(title)
        assertTrue(store.recordCheck("comick:hid1", listOf(chapter(1))).isEmpty())

        val found = store.recordCheck("comick:hid1", listOf(chapter(1), chapter(2)))

        assertEquals(listOf("c2"), found.map { it.id })
        assertEquals(listOf("c2"), store.entry(title)!!.newChapters.map { it.chapter.id })
        // Opening the new chapter clears it.
        store.recordProgress(title, chapter(2), page = 0)
        assertTrue(store.entry(title)!!.newChapters.isEmpty())
    }

    @Test
    fun deletingACategoryTakesItOffTitles() {
        val store = store()
        store.add(title, categories = listOf("A", "B"))
        store.deleteCategory("A")
        assertEquals(listOf("B"), store.entry(title)!!.categories)
        assertEquals(listOf("B"), store.data.value.categories)
    }

    @Test
    fun aDamagedFileIsKeptAsideAndTheLibraryStartsEmpty() {
        file.writeText("{ not json")
        val store = store()
        assertTrue(store.entries.isEmpty())
        assertTrue(File(dir, "library.json.broken").exists())
        store.add(title)
        assertFalse(file.readText().contains("not json"))
    }
}

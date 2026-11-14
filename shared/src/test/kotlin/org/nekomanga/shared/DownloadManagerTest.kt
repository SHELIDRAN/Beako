package org.nekomanga.shared

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.nekomanga.shared.download.DownloadManager
import org.nekomanga.shared.download.DownloadState
import org.nekomanga.shared.download.ImageFetcher
import org.nekomanga.shared.library.Settings
import org.nekomanga.shared.library.SettingsStore
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.PageList
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerTest {

    private val root: File = Files.createTempDirectory("beako-dl").toFile()
    private val title = Title(Sources.COMICK, "hid", "What: A/Title?")

    private fun chapter(n: Int, external: String? = null) =
        Chapter(
            Sources.MANGADEX,
            "c$n",
            "$n",
            language = "en",
            publishedAt = "",
            pages = 2,
            externalUrl = external,
        )

    private val pages: suspend (Chapter) -> PageList = { c ->
        PageList(listOf("https://img.test/${c.id}/1.png", "https://img.test/${c.id}/2.jpg?x=1"))
    }

    @Test
    fun chaptersDownloadInOrderAndCanBeReadOffline() = runTest {
        val fetched = mutableListOf<String>()
        val manager =
            DownloadManager(
                root,
                backgroundScope,
                pages,
                ImageFetcher { url ->
                    fetched += url
                    url.toByteArray()
                },
            )

        manager.enqueue(
            title,
            listOf(chapter(1), chapter(2), chapter(3, external = "https://publisher.test")),
        )
        runCurrent()

        assertTrue(manager.queue.value.isEmpty())
        assertEquals(4, fetched.size)
        val files = assertNotNull(manager.localPages(title, chapter(2)))
        assertEquals(listOf("001.png", "002.jpg"), files.map { it.name })
        assertEquals("https://img.test/c2/1.png", files[0].readText())
        // Publisher-hosted chapters can't be downloaded.
        assertFalse(manager.isDownloaded(title, chapter(3)))
        assertEquals(setOf("comick:hid"), manager.downloadedTitleKeys())
        // Names are safe on Windows.
        assertTrue(root.listFiles()!!.single().name.none { it in ":/?" })

        manager.delete(title, chapter(1))
        assertNull(manager.localPages(title, chapter(1)))
    }

    @Test
    fun aFailedChapterStaysQueuedAsFailedAndLeavesNoPartialChapter() = runTest {
        val manager =
            DownloadManager(
                root,
                backgroundScope,
                pages,
                ImageFetcher { url ->
                    if (url.endsWith("2.jpg?x=1")) throw IOException("connection reset")
                    else ByteArray(1)
                },
            )

        manager.enqueue(title, listOf(chapter(1)))
        runCurrent()

        val job = manager.queue.value.single()
        assertEquals(DownloadState.FAILED, job.state)
        assertEquals("connection reset", job.error)
        assertFalse(manager.isDownloaded(title, chapter(1)))
    }

    @Test
    fun settingsSurviveARestart() {
        val file = File(root, "settings.json")
        SettingsStore(file).update { it.copy(incognito = true, updateIntervalHours = 12) }
        val reopened = SettingsStore(file).current
        assertEquals(Settings(incognito = true, updateIntervalHours = 12), reopened)
    }

    @Test
    fun downloadsStayFoundWhenTheTitleIsRenamed() = runTest {
        val manager = DownloadManager(root, backgroundScope, pages, ImageFetcher { ByteArray(1) })
        manager.enqueue(title, listOf(chapter(1)))
        runCurrent()

        val renamed = title.copy(title = "A completely different display name")
        assertTrue(manager.isDownloaded(renamed, chapter(1).copy(group = "New group")))
        assertEquals(setOf("comick:hid"), manager.downloadedTitleKeys())
    }

    @Test
    fun longNamesKeepTheirIdSoTitlesNeverShareAFolder() = runTest {
        val manager = DownloadManager(root, backgroundScope, pages, ImageFetcher { ByteArray(1) })
        val long = "x".repeat(300)
        val first = Title(Sources.COMICK, "one", long)
        val second = Title(Sources.COMICK, "two", long)
        manager.enqueue(first, listOf(chapter(1)))
        manager.enqueue(second, listOf(chapter(1)))
        runCurrent()

        val folders = root.listFiles()!!.map { it.name }.sorted()
        assertEquals(2, folders.size)
        assertTrue(folders.all { it.length <= 120 && it.endsWith("]") })
    }

    @Test
    fun aChapterQueuedAfterTheQueueEmptiedStillDownloads() = runTest {
        val manager = DownloadManager(root, backgroundScope, pages, ImageFetcher { ByteArray(1) })
        manager.enqueue(title, listOf(chapter(1)))
        runCurrent()
        manager.enqueue(title, listOf(chapter(2)))
        runCurrent()
        assertTrue(manager.isDownloaded(title, chapter(2)))
        assertTrue(manager.queue.value.isEmpty())
    }

    @Test
    fun pauseAndResumeFinishTheChapterOnce() = runTest {
        var fetches = 0
        val manager =
            DownloadManager(
                root,
                backgroundScope,
                pages,
                ImageFetcher {
                    fetches++
                    ByteArray(1)
                },
            )
        manager.enqueue(title, listOf(chapter(1)))
        manager.pause()
        manager.resume()
        runCurrent()
        assertTrue(manager.isDownloaded(title, chapter(1)))
        assertEquals(2, fetches)
    }
}

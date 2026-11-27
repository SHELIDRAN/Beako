package org.nekomanga.shared

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.nekomanga.shared.backup.Backups
import org.nekomanga.shared.model.ComicType
import org.nekomanga.shared.model.Sources

class PrototypeLibraryTest {
    private val prototype =
        """{"entries":[
             {"manga":{"id":"a96676e5-8ae2-425e-b549-7f15dd34a6d8","title":"Komi Can't Communicate",
                       "cover_file":"cover.jpg","status":"completed","year":2016,"original_language":"ja",
                       "tags":["Comedy","Romance"]},"added":1700000000},
             {"manga":{"id":"b2","title":"Second"},"added":1700000100}],
           "progress":{"a96676e5-8ae2-425e-b549-7f15dd34a6d8":
             {"chapter_id":"c9","chapter_name":"Ch. 9","page":4,"read":["c1","c2"],"updated":1700000500}},
           "settings":{},"statuses":{},"comick":{}}"""

    @Test
    fun theRustPrototypesLibraryRestoresLikeABackup() {
        val file = Files.createTempFile("library", ".json").toFile()
        try {
            file.writeText(prototype)
            val (library, settings) = Backups.read(file)
            assertNull(settings)
            assertEquals(2, library.entries.size)
            val komi = library.entries.first()
            assertEquals(Sources.MANGADEX, komi.title.source)
            assertEquals("a96676e5-8ae2-425e-b549-7f15dd34a6d8", komi.mangaDexId)
            assertEquals(ComicType.MANGA, komi.title.type)
            assertEquals("jp", komi.title.country)
            assertEquals(
                "https://uploads.mangadex.org/covers/a96676e5-8ae2-425e-b549-7f15dd34a6d8/cover.jpg.512.jpg",
                komi.title.coverUrl,
            )
            assertEquals(setOf("c1", "c2"), komi.readChapterIds)
            assertEquals("c9", komi.progress!!.chapterId)
            assertEquals(4, komi.progress!!.page)
            assertEquals(1_700_000_500_000, komi.progress!!.updatedAt)
            assertEquals(1_700_000_000_000, komi.addedAt)
            assertNull(library.entries[1].progress)
        } finally {
            file.delete()
        }
    }

    @Test
    fun theDefaultLocationIsOnlyOfferedWhenTheFileExists() {
        val home = Files.createTempDirectory("appdata").toFile()
        try {
            assertNull(org.nekomanga.shared.backup.PrototypeLibrary.defaultFile { home.path })
            File(home, "Neko/Neko Desktop/data").mkdirs()
            File(home, "Neko/Neko Desktop/data/library.json").writeText(prototype)
            assertEquals(
                "library.json",
                org.nekomanga.shared.backup.PrototypeLibrary.defaultFile { home.path }!!.name,
            )
        } finally {
            home.deleteRecursively()
        }
    }
}
